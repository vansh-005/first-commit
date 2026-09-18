package com.memorylayer.api.dto;

import java.util.List;

/** Docs/API.md §18. {@code filters} is the only client-influenceable part of the query —
 * {@code userId} is never accepted here; it always comes from the authenticated JWT. */
public record SearchRequest(String query, Integer limit, SearchFilters filters) {

    public record SearchFilters(List<String> mediaCategories) {
    }
}
