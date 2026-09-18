package com.memorylayer.api.dto;

public record UploadResult(String clientFileId, String documentId, String status, UploadInstructions upload) {
}
