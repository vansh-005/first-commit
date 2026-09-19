package com.memorylayer.api.ask;

import com.memorylayer.api.dto.MediaTimestamp;
import com.memorylayer.api.search.RetrievalContentMapper;
import software.amazon.awssdk.services.bedrockagentruntime.model.Citation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievedReference;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Flattens Bedrock {@code RetrieveAndGenerate} citations (each citing zero or more grounding
 * references) into a deduplicated, document-centric source list.
 *
 * <p>Dedup key is {@code (documentId, startMs, endMs)}, not {@code documentId} alone (Phase 6
 * amendment): a single answer can legitimately cite two different moments of the same
 * audio/video file, and collapsing those into one source card would silently drop a real
 * citation. References with no timestamp (documents, images) still dedupe by
 * {@code documentId} alone, since there is no finer-grained "moment" to distinguish.
 *
 * <p>Order is preserved as first-occurrence in the order citations appear in the generated
 * answer — unlike {@code SearchResultMapper}, there is no per-reference relevance score to
 * rank by here.
 */
final class AskCitationMapper {

    private AskCitationMapper() {
    }

    record DedupedCitation(String documentId, String snippet, MediaTimestamp mediaTimestamp) {
    }

    static List<DedupedCitation> dedupeCitations(List<Citation> citations) {
        List<DedupedCitation> deduped = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (Citation citation : citations) {
            if (!citation.hasRetrievedReferences()) {
                continue;
            }
            for (RetrievedReference reference : citation.retrievedReferences()) {
                RetrievalContentMapper.ChunkRef chunkRef = RetrievalContentMapper.ChunkRef.of(reference);
                String documentId = RetrievalContentMapper.extractDocumentId(chunkRef);
                if (documentId == null) {
                    continue;
                }
                MediaTimestamp mediaTimestamp = RetrievalContentMapper.resolveMediaTimestamp(chunkRef.metadata());
                String dedupeKey = dedupeKey(documentId, mediaTimestamp);
                if (!seenKeys.add(dedupeKey)) {
                    continue;
                }
                deduped.add(new DedupedCitation(documentId, RetrievalContentMapper.resolveSnippet(chunkRef), mediaTimestamp));
            }
        }
        return deduped;
    }

    private static String dedupeKey(String documentId, MediaTimestamp mediaTimestamp) {
        if (mediaTimestamp == null) {
            return documentId;
        }
        return documentId + "@" + mediaTimestamp.startMs() + "-" + mediaTimestamp.endMs();
    }
}
