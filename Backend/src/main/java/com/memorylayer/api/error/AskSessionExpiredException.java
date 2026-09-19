package com.memorylayer.api.error;

/** Thrown when a client-supplied application {@code sessionId} doesn't resolve under the
 * authenticated user's own DynamoDB partition — either it never existed, it belonged to a
 * different user (which must look identical to "never existed"), it already expired via TTL,
 * or the underlying Bedrock session it pointed to was rejected as invalid/expired. In every
 * case the correct client behavior is the same: start a new conversation, not retry. */
public class AskSessionExpiredException extends RuntimeException {
}
