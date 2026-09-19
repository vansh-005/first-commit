package com.memorylayer.api.ingestion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaleDocumentPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");

    @Test
    void uploadPendingIsNotStaleBeforeThirtyMinutes() {
        Instant createdAt = NOW.minusSeconds(29 * 60);
        assertThat(StaleDocumentPolicy.isUploadPendingStale(createdAt, NOW)).isFalse();
    }

    @Test
    void uploadPendingIsStaleAtExactlyThirtyMinutes() {
        Instant createdAt = NOW.minusSeconds(30 * 60);
        assertThat(StaleDocumentPolicy.isUploadPendingStale(createdAt, NOW)).isTrue();
    }

    @Test
    void uploadPendingIsStaleWellPastThirtyMinutes() {
        Instant createdAt = NOW.minusSeconds(45 * 60);
        assertThat(StaleDocumentPolicy.isUploadPendingStale(createdAt, NOW)).isTrue();
    }

    @Test
    void uploadedIsNotStaleWithinTheNormalSqsBackpressureWindow() {
        // ~100 minutes is the documented normal SQS retry budget (Docs/ARCHITECTURE.md
        // §7.3.2) — must never be false-failed by this policy.
        Instant uploadedAt = NOW.minusSeconds(100 * 60);
        assertThat(StaleDocumentPolicy.isUploadedStale(uploadedAt, NOW)).isFalse();
    }

    @Test
    void uploadedIsStaleAtExactlyTwoHours() {
        Instant uploadedAt = NOW.minusSeconds(2 * 60 * 60);
        assertThat(StaleDocumentPolicy.isUploadedStale(uploadedAt, NOW)).isTrue();
    }

    @Test
    void uploadedIsNotStaleJustUnderTwoHours() {
        Instant uploadedAt = NOW.minusSeconds(2 * 60 * 60 - 1);
        assertThat(StaleDocumentPolicy.isUploadedStale(uploadedAt, NOW)).isFalse();
    }

    @Test
    void indexingIsNotWarnableBeforeOneHour() {
        Instant updatedAt = NOW.minusSeconds(59 * 60);
        assertThat(StaleDocumentPolicy.isIndexingWarnable(updatedAt, NOW)).isFalse();
    }

    @Test
    void indexingIsWarnableAtExactlyOneHour() {
        Instant updatedAt = NOW.minusSeconds(60 * 60);
        assertThat(StaleDocumentPolicy.isIndexingWarnable(updatedAt, NOW)).isTrue();
    }

    @Test
    void aNullReferenceTimeIsNeverConsideredStale() {
        assertThat(StaleDocumentPolicy.isUploadPendingStale(null, NOW)).isFalse();
        assertThat(StaleDocumentPolicy.isUploadedStale(null, NOW)).isFalse();
        assertThat(StaleDocumentPolicy.isIndexingWarnable(null, NOW)).isFalse();
    }

    @Test
    void activeJobDocumentRefsIncludesStartingAndInProgressJobs() {
        IngestionJob starting = job(IngestionJobStatus.STARTING, "user-1#doc-1");
        IngestionJob inProgress = job(IngestionJobStatus.IN_PROGRESS, "user-2#doc-2");

        assertThat(StaleDocumentPolicy.activeJobDocumentRefs(List.of(starting, inProgress)))
                .containsExactlyInAnyOrder("user-1#doc-1", "user-2#doc-2");
    }

    @Test
    void activeJobDocumentRefsExcludesTerminalJobs() {
        IngestionJob complete = job(IngestionJobStatus.COMPLETE, "user-1#doc-1");
        IngestionJob failed = job(IngestionJobStatus.FAILED, "user-2#doc-2");
        IngestionJob stopped = job(IngestionJobStatus.STOPPED, "user-3#doc-3");

        assertThat(StaleDocumentPolicy.activeJobDocumentRefs(List.of(complete, failed, stopped))).isEmpty();
    }

    @Test
    void activeJobDocumentRefsIsEmptyForNoJobs() {
        assertThat(StaleDocumentPolicy.activeJobDocumentRefs(List.of())).isEmpty();
    }

    private static IngestionJob job(IngestionJobStatus status, String... documentRefs) {
        IngestionJob job = new IngestionJob();
        job.setJobId("job-" + status);
        job.setStatus(status);
        job.setDocumentIds(List.of(documentRefs));
        return job;
    }
}
