package com.memorylayer.api.dto;

import java.util.Map;

public record UploadInstructions(String method, String url, Map<String, String> headers, String expiresAt) {
}
