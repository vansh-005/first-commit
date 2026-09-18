package com.memorylayer.api.ingestion;

/** Mirrors Bedrock's own {@code IngestionJobStatus} values so the reconciler can store
 * {@code GetIngestionJob}'s response directly without translation. */
public enum IngestionJobStatus {
    STARTING,
    IN_PROGRESS,
    COMPLETE,
    FAILED,
    STOPPING,
    STOPPED
}
