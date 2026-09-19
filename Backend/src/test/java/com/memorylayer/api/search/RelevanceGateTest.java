package com.memorylayer.api.search;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelevanceGateTest {

    private static final double MIN = KnowledgeBaseRetriever.DEFAULT_MIN_RELEVANCE_SCORE;

    private static KnowledgeBaseRetrievalResult chunk(String documentId, Double score) {
        return KnowledgeBaseRetrievalResult.builder()
                .content(c -> c.text("text"))
                .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                .score(score)
                .build();
    }

    /**
     * Best chunk score per query measured live against the real Knowledge Base for queries whose
     * subject genuinely does not exist in the account (bills, flights, recipes, "boiling point of
     * mercury on Jupiter", "How much AWS credit did I have?" ... 45+ queries, 3 accounts). The worst
     * offender was 0.5955. None of these may ever produce a result.
     */
    private static final double[] MEASURED_ABSENT_BEST_SCORES = {
            0.5341, 0.5613, 0.5341, 0.5374, 0.5577, 0.5366, 0.5573, 0.5955, 0.5229, 0.5365, 0.5263,
            0.5566, 0.5529, 0.5452, 0.5326, 0.5367, 0.5798, 0.5788, 0.5740, 0.5695, 0.5646};

    /** Best chunk scores measured for clearly relevant queries and natural paraphrases of them. */
    private static final double[] MEASURED_RELEVANT_BEST_SCORES = {
            0.8071, 0.8523, 0.7853, 0.7818, 0.7368, 0.8122, 0.7311, 0.6811, 0.6462, 0.6437, 0.6398};

    @Test
    void theDefaultThresholdSitsAboveEveryMeasuredAbsentScoreAndBelowEveryMeasuredRelevantScore() {
        for (double absent : MEASURED_ABSENT_BEST_SCORES) {
            assertThat(absent).as("absent-query score %s", absent).isLessThan(MIN);
        }
        for (double relevant : MEASURED_RELEVANT_BEST_SCORES) {
            assertThat(relevant).as("relevant-query score %s", relevant).isGreaterThanOrEqualTo(MIN);
        }
    }

    @Test
    void anAbsentQueryYieldsNoChunksAtAll() {
        List<KnowledgeBaseRetrievalResult> nearestNeighbours = java.util.Arrays.stream(MEASURED_ABSENT_BEST_SCORES)
                .mapToObj(score -> chunk("doc-" + score, score))
                .toList();

        assertThat(RelevanceGate.filter(nearestNeighbours, MIN)).isEmpty();
    }

    @Test
    void keepsOnlyChunksAtOrAboveTheThresholdPreservingOrder() {
        List<KnowledgeBaseRetrievalResult> results = List.of(
                chunk("a", 0.81), chunk("b", 0.55), chunk("a", 0.54), chunk("c", MIN), chunk("d", 0.6199));

        List<KnowledgeBaseRetrievalResult> kept = RelevanceGate.filter(results, MIN);

        assertThat(kept).extracting(r -> r.metadata().get("documentId").asString()).containsExactly("a", "c");
    }

    @Test
    void aChunkWithNoScoreIsTreatedAsIrrelevant() {
        assertThat(RelevanceGate.filter(List.of(chunk("a", null)), MIN)).isEmpty();
    }

    @Test
    void handlesNullAndEmptyInput() {
        assertThat(RelevanceGate.filter(null, MIN)).isEmpty();
        assertThat(RelevanceGate.filter(List.of(), MIN)).isEmpty();
    }
}
