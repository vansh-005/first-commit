package com.memorylayer.api.dto;

/** Docs/API.md §18-19. {@code score} is exposed for ranking/debugging only — never presented
 * to users as an absolute confidence percentage (frontend concern, not enforced here). */
public record SearchResultMatch(double score, String snippet, MediaTimestamp mediaTimestamp) {
}
