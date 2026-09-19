package com.memorylayer.api.search;

import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;

import java.util.List;

/**
 * The relevance gate: drops retrieval chunks whose similarity score is below a minimum.
 *
 * <p>A vector search always returns its k nearest neighbours, however unrelated — so without a
 * gate, "my electricity bill" returns whatever documents happen to be closest. Applied per
 * <i>chunk</i>, <b>before</b> document deduplication, so a document only survives if at least one
 * of its chunks is genuinely relevant.
 *
 * <p>The threshold is measured, not guessed (see {@link KnowledgeBaseRetriever}). Scores are used
 * only for this decision — they are never logged.
 */
public final class RelevanceGate {

    private RelevanceGate() {
    }

    /** Keeps chunks scoring at or above {@code minScore}, preserving Bedrock's descending order.
     * A chunk with no score is treated as irrelevant. */
    public static List<KnowledgeBaseRetrievalResult> filter(List<KnowledgeBaseRetrievalResult> results, double minScore) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(result -> result.score() != null && result.score() >= minScore)
                .toList();
    }
}
