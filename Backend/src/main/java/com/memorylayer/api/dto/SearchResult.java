package com.memorylayer.api.dto;

/** Docs/API.md §18. One entry per matching document — deduplicated from potentially several
 * matching chunks (see SearchResultMapper). */
public record SearchResult(SearchResultDocument document, SearchResultMatch match) {
}
