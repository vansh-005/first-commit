package com.memorylayer.api.dto;

/** Docs/API.md §19. Present only for audio/video chunks that carry timing metadata;
 * absent (null) otherwise. */
public record MediaTimestamp(long startMs, long endMs) {
}
