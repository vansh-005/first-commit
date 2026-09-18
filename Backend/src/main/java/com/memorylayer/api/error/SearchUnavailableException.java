package com.memorylayer.api.error;

/** Thrown when the downstream Bedrock {@code Retrieve} call fails. {@code retryable}
 * distinguishes throttling (safe to retry shortly) from other upstream failures. */
public class SearchUnavailableException extends RuntimeException {

    private final boolean retryable;

    public SearchUnavailableException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
