package com.memorylayer.api.document;

/** Docs/DATA_MODEL.md §6. Phase 3 only ever writes/reads UPLOAD_PENDING; the rest of the
 * lifecycle is driven by the Phase 4 ingestion pipeline. */
public enum DocumentStatus {
    UPLOAD_PENDING,
    UPLOADED,
    INDEXING,
    READY,
    FAILED
}
