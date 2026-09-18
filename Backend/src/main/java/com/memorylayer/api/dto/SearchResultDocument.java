package com.memorylayer.api.dto;

import com.memorylayer.api.document.MediaCategory;

/** Docs/API.md §18. Resolved from DynamoDB, never from raw Bedrock metadata directly —
 * the retrieval result only supplies the documentId used to look this up. */
public record SearchResultDocument(String documentId, String fileName, MediaCategory mediaCategory, String mimeType) {
}
