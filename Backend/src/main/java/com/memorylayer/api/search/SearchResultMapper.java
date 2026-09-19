package com.memorylayer.api.search;

import com.memorylayer.api.dto.MediaTimestamp;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Document-centric dedup/ranking over raw {@code Retrieve} chunks. The actual per-chunk
 * mapping (snippet resolution, documentId extraction, media-timestamp resolution) lives in
 * {@link RetrievalContentMapper}, shared with Ask's citation mapping (Phase 6) — the methods
 * here are thin, signature-preserving delegators kept for existing call sites/tests.
 */
final class SearchResultMapper {

    private SearchResultMapper() {
    }

    record DedupedMatch(String documentId, double score, String snippet, MediaTimestamp mediaTimestamp) {
    }

    /** Groups chunk-level results by documentId, keeping the first (highest-scoring, since
     * Bedrock already returns results sorted by descending score) chunk per document, up to
     * {@code limit} documents. */
    static List<DedupedMatch> dedupeAndRank(List<KnowledgeBaseRetrievalResult> results, int limit) {
        List<DedupedMatch> deduped = new ArrayList<>();
        Set<String> seenDocumentIds = new HashSet<>();

        for (KnowledgeBaseRetrievalResult result : results) {
            if (deduped.size() >= limit) {
                break;
            }
            String documentId = extractDocumentId(result);
            if (documentId == null || !seenDocumentIds.add(documentId)) {
                continue;
            }
            double score = result.score() == null ? 0.0 : result.score();
            deduped.add(new DedupedMatch(documentId, score, resolveSnippet(result), resolveMediaTimestamp(result.metadata())));
        }
        return deduped;
    }

    static String extractDocumentId(KnowledgeBaseRetrievalResult result) {
        return RetrievalContentMapper.extractDocumentId(RetrievalContentMapper.ChunkRef.of(result));
    }

    static String resolveSnippet(KnowledgeBaseRetrievalResult result) {
        return RetrievalContentMapper.resolveSnippet(RetrievalContentMapper.ChunkRef.of(result));
    }

    static MediaTimestamp resolveMediaTimestamp(Map<String, Document> metadata) {
        return RetrievalContentMapper.resolveMediaTimestamp(metadata);
    }
}
