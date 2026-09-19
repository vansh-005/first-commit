package com.memorylayer.api.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure staleness thresholds for Phase 7's stale-document cleanup, kept logically and
 * physically separate from {@link StatusReconcilerHandler} (Bedrock ingestion-job status
 * remains that Lambda's sole authority; this class never touches Bedrock). Extracted from
 * {@link StaleDocumentCleanupHandler} so the actual time-math is unit-testable without any AWS
 * client.
 *
 * <p>Per the approved Phase 7 plan:
 * <ul>
 *   <li>{@code UPLOAD_PENDING} past 30 minutes: the handler checks whether the expected S3
 *       object exists; if not, the document is failed. If it does exist, the handler leaves it
 *       alone (an object that landed without its event ever reaching this pipeline is an
 *       unexplained edge case, not something to guess a resolution for).</li>
 *   <li>{@code UPLOADED} past 2 hours: auto-failed. Two hours comfortably exceeds the
 *       documented ~100-minute SQS retry budget (Docs/ARCHITECTURE.md §7.3.2), so a document
 *       still {@code UPLOADED} at that point has already fallen out of normal backpressure
 *       retry, not been caught mid-retry.</li>
 *   <li>{@code INDEXING} past 1 hour: never auto-failed — Bedrock's own ingestion-job status,
 *       polled by {@link StatusReconcilerHandler}, remains the sole authority for this state.
 *       Only a structured warning is emitted for operational visibility.</li>
 * </ul>
 */
final class StaleDocumentPolicy {

    static final Duration UPLOAD_PENDING_STALE_AFTER = Duration.ofMinutes(30);
    static final Duration UPLOADED_STALE_AFTER = Duration.ofHours(2);
    static final Duration INDEXING_WARNING_AFTER = Duration.ofHours(1);

    private StaleDocumentPolicy() {
    }

    static boolean isUploadPendingStale(Instant createdAt, Instant now) {
        return isStale(createdAt, now, UPLOAD_PENDING_STALE_AFTER);
    }

    static boolean isUploadedStale(Instant referenceTime, Instant now) {
        return isStale(referenceTime, now, UPLOADED_STALE_AFTER);
    }

    static boolean isIndexingWarnable(Instant referenceTime, Instant now) {
        return isStale(referenceTime, now, INDEXING_WARNING_AFTER);
    }

    private static boolean isStale(Instant referenceTime, Instant now, Duration threshold) {
        if (referenceTime == null) {
            return false;
        }
        return Duration.between(referenceTime, now).compareTo(threshold) >= 0;
    }

    /** {@code userId#documentId} refs covered by a job Bedrock is still actively running
     * ({@code STARTING}/{@code IN_PROGRESS} — the same "still running" predicate
     * {@link StatusReconcilerHandler} uses). Stale-{@code UPLOADED} cleanup must never
     * auto-fail a document in this set: {@code StartIngestionJob} is a real, non-retractable
     * side effect (see {@code IngestionCoordinatorHandler.startJobFor}'s Javadoc), so a
     * document can be genuinely covered by an in-flight job even though its own DynamoDB
     * status write to {@code INDEXING} failed and left it stuck at {@code UPLOADED}.
     * Auto-failing it here would mark a document {@code FAILED} that Bedrock may still index
     * successfully — only the Status Reconciler resolves this deterministically once the job
     * finishes. A job already {@code COMPLETE}/{@code FAILED}/{@code STOPPED} is deliberately
     * excluded — that is a separate, rarer orphan case (the reconciler only ever updates
     * documents still at {@code INDEXING}, so a document that never got there is not resolved
     * even after the job finishes) that stale cleanup's 2-hour auto-fail still catches, matching
     * the approved Phase 7 UPLOADED-staleness decision. */
    static Set<String> activeJobDocumentRefs(List<IngestionJob> jobs) {
        Set<String> refs = new HashSet<>();
        for (IngestionJob job : jobs) {
            if (job.getStatus() == IngestionJobStatus.STARTING || job.getStatus() == IngestionJobStatus.IN_PROGRESS) {
                refs.addAll(job.getDocumentIds());
            }
        }
        return refs;
    }
}
