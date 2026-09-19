package com.memorylayer.api.dto;

import com.memorylayer.api.document.MediaCategory;

/** Docs/API.md §20. Deliberately carries no presigned {@code accessUrl} — the frontend
 * resolves a clickable source through the existing, ownership-checked
 * {@code GET /documents/{id}/access-url} on click, the same way search results do (Phase 6
 * amendment, approved over embedding a URL here). */
public record Citation(String citationId, String documentId, String fileName, MediaCategory mediaCategory,
                        String mimeType, String snippet, MediaTimestamp mediaTimestamp) {
}
