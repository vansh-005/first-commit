package com.memorylayer.api.dto;

import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.document.DocumentStatus;

/** Shared shape for both GET /api/v1/documents items and GET /api/v1/documents/{id}.
 * Docs/API.md §15-16. Never includes the raw S3 key. */
public record DocumentSummary(
        String documentId,
        String fileName,
        MediaCategory mediaCategory,
        String mimeType,
        long sizeBytes,
        DocumentStatus status,
        String createdAt,
        String uploadedAt,
        String updatedAt,
        String failureReason) {
}
