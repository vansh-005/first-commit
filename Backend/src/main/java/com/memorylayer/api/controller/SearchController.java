package com.memorylayer.api.controller;

import com.memorylayer.api.dto.SearchRequest;
import com.memorylayer.api.dto.SearchResponse;
import com.memorylayer.api.search.SearchService;
import com.memorylayer.api.security.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Docs/API.md §18. Uses Bedrock {@code Retrieve} only — never {@code RetrieveAndGenerate},
 * which is Phase 6's {@code /ask}. */
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/api/v1/search")
    public SearchResponse search(HttpServletRequest request, @RequestBody SearchRequest searchRequest) {
        String userId = AuthenticatedUserResolver.resolveUserId(request);
        return searchService.search(userId, searchRequest);
    }
}
