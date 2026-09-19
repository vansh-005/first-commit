package com.memorylayer.api.error;

/** Thrown when a downstream Bedrock {@code Retrieve} or {@code RetrieveAndGenerate} call
 * fails. {@code retryable} distinguishes throttling (safe to retry shortly) from other
 * upstream failures. Shared by {@code /search} and {@code /ask} — the failure mode is the
 * same Bedrock dependency being unavailable, not a search-specific concern. */
public class RetrievalUnavailableException extends RuntimeException {

    private final boolean retryable;

    public RetrievalUnavailableException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
