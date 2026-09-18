package com.memorylayer.api.dto;

/** One file within a POST /api/v1/uploads request body. Docs/API.md §10. */
public record UploadFileRequest(String clientFileId, String fileName, String contentType, Long sizeBytes) {
}
