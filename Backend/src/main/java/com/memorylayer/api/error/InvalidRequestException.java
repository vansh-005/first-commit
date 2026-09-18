package com.memorylayer.api.error;

/** A validation failure that is safe to describe back to the client (e.g. missing/invalid
 * upload fields, an unreadable pagination cursor). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
