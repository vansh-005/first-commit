package com.memorylayer.api.dto;

import java.util.List;

/** Docs/API.md §20. {@code sessionId} is an opaque application-issued identifier, not a raw
 * Bedrock session ID — pass it back on the next {@code /ask} call to continue the same
 * conversation (Phase 6 amendment). */
public record AskResponse(String answer, String sessionId, List<Citation> citations) {
}
