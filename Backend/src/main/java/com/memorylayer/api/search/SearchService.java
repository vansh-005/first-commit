package com.memorylayer.api.search;

import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.dto.SearchRequest;
import com.memorylayer.api.dto.SearchResponse;
import com.memorylayer.api.dto.SearchResult;
import com.memorylayer.api.dto.SearchResultDocument;
import com.memorylayer.api.dto.SearchResultMatch;
import com.memorylayer.api.error.InvalidRequestException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.document.Document.ListBuilder;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Docs/API.md §18. Every retrieval injects {@code userId == authenticatedUserId} server-side
 * — the request DTO has no field a client could use to set or override it (Docs/DATA_MODEL.md
 * §10, Docs/ARCHITECTURE.md).
 */
@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;
    // Multiple chunks can belong to the same document, so more raw chunks than the final
    // document limit are requested and then deduplicated (see SearchResultMapper).
    private static final int OVERSAMPLE_FACTOR = 3;
    private static final int MAX_RAW_RESULTS = 50;

    private final KnowledgeBaseRetriever retriever;
    private final DocumentRepository documentRepository;

    public SearchService(KnowledgeBaseRetriever retriever, DocumentRepository documentRepository) {
        this.retriever = retriever;
        this.documentRepository = documentRepository;
    }

    public SearchResponse search(String userId, SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new InvalidRequestException("query is required");
        }

        int limit = resolveLimit(request.limit());
        List<MediaCategory> mediaCategories = resolveMediaCategories(request.filters());
        int numberOfResults = Math.min(limit * OVERSAMPLE_FACTOR, MAX_RAW_RESULTS);

        // The relevance gate runs inside the retriever, per chunk and *before* the document dedup
        // below — so a query with nothing relevant yields [] rather than nearest-neighbour noise.
        List<KnowledgeBaseRetrievalResult> relevantChunks =
                retriever.retrieveRelevant(buildFilter(userId, mediaCategories), request.query(), numberOfResults);

        List<SearchResultMapper.DedupedMatch> matches = SearchResultMapper.dedupeAndRank(relevantChunks, limit);

        List<SearchResult> results = new ArrayList<>();
        for (SearchResultMapper.DedupedMatch match : matches) {
            documentRepository.findByUserAndDocumentId(userId, match.documentId())
                    .ifPresent(document -> results.add(toSearchResult(document, match)));
        }

        return new SearchResponse(request.query(), results);
    }

    private static SearchResult toSearchResult(Document document, SearchResultMapper.DedupedMatch match) {
        SearchResultDocument resultDocument = new SearchResultDocument(
                document.getDocumentId(), document.getFileName(), document.getMediaCategory(), document.getMimeType());
        SearchResultMatch resultMatch = new SearchResultMatch(match.score(), match.snippet(), match.mediaTimestamp());
        return new SearchResult(resultDocument, resultMatch);
    }

    private static int resolveLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static List<MediaCategory> resolveMediaCategories(SearchRequest.SearchFilters filters) {
        if (filters == null || filters.mediaCategories() == null || filters.mediaCategories().isEmpty()) {
            return List.of();
        }
        List<MediaCategory> categories = new ArrayList<>();
        for (String raw : filters.mediaCategories()) {
            try {
                categories.add(MediaCategory.valueOf(raw));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Unknown mediaCategory: " + raw);
            }
        }
        return categories;
    }

    /** {@code userId} is always the top-level (or first {@code andAll}) clause — the only
     * client-influenceable addition is an optional {@code mediaCategory} "in" clause, which is
     * additive (AND), never a replacement. */
    private static RetrievalFilter buildFilter(String userId, List<MediaCategory> mediaCategories) {
        RetrievalFilter userFilter = RetrievalFilters.forUser(userId);

        if (mediaCategories.isEmpty()) {
            return userFilter;
        }

        ListBuilder categoryListBuilder = software.amazon.awssdk.core.document.Document.listBuilder();
        for (MediaCategory category : mediaCategories) {
            categoryListBuilder.addString(category.name());
        }

        RetrievalFilter categoryFilter = RetrievalFilter.builder()
                .in(FilterAttribute.builder()
                        .key("mediaCategory")
                        .value(categoryListBuilder.build())
                        .build())
                .build();

        return RetrievalFilter.builder().andAll(userFilter, categoryFilter).build();
    }
}
