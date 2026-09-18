package com.memorylayer.api.controller;

import com.memorylayer.api.dto.MeResponse;
import com.memorylayer.api.security.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal Phase 2 diagnostic route (see Docs/API.md) proving the Cognito JWT authorizer
 * and custom scope requirement work end to end. Not a permanent product endpoint.
 */
@RestController
public class MeController {

    @GetMapping("/api/v1/me")
    public MeResponse me(HttpServletRequest request) {
        return new MeResponse(AuthenticatedUserResolver.resolveUserId(request));
    }
}
