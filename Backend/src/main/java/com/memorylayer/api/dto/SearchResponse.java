package com.memorylayer.api.dto;

import java.util.List;

/** Docs/API.md §18. */
public record SearchResponse(String query, List<SearchResult> results) {
}
