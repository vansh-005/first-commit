package com.memorylayer.api.controller;

import com.memorylayer.api.ask.AskService;
import com.memorylayer.api.dto.AskRequest;
import com.memorylayer.api.dto.AskResponse;
import com.memorylayer.api.security.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Docs/API.md §20. Uses Bedrock {@code RetrieveAndGenerate} — grounded Q&A with citations,
 * never plain unsourced generation. */
@RestController
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/api/v1/ask")
    public AskResponse ask(HttpServletRequest request, @RequestBody AskRequest askRequest) {
        String userId = AuthenticatedUserResolver.resolveUserId(request);
        return askService.ask(userId, askRequest);
    }
}
