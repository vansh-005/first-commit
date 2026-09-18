package com.memorylayer.api.error;

/** Thrown when a document doesn't exist in the authenticated user's own partition — either
 * it never existed, or it belongs to a different user, which must look identical. */
public class DocumentNotFoundException extends RuntimeException {
}
