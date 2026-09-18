package com.memorylayer.api.dto;

/** Standard error envelope defined in Docs/API.md Section 6. */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, String requestId, boolean retryable) {
    }
}
