package com.memorylayer.api.dto;

/** Docs/API.md §20. {@code sessionId} is an opaque, application-issued identifier previously
 * returned from a prior {@code /ask} call — never a raw Bedrock session ID (Phase 6
 * amendment). {@code userId}/{@code tenantId} are never accepted here; they always come from
 * the authenticated JWT. */
public record AskRequest(String question, String sessionId) {
}
