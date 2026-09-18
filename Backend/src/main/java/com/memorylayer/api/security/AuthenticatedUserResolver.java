package com.memorylayer.api.security;

import com.amazonaws.serverless.proxy.RequestReader;
import com.amazonaws.serverless.proxy.model.HttpApiV2AuthorizerMap;
import com.amazonaws.serverless.proxy.model.HttpApiV2JwtAuthorizer;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequestContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts the canonical userId (Cognito JWT {@code sub}) from the API Gateway JWT
 * authorizer context that aws-serverless-java-container attaches to the servlet request.
 * API Gateway has already validated the token's signature, issuer, audience, and required
 * scope before the Lambda runs — this only reads the already-validated claims.
 */
public final class AuthenticatedUserResolver {

    private AuthenticatedUserResolver() {
    }

    public static String resolveUserId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestReader.HTTP_API_CONTEXT_PROPERTY);
        if (!(attribute instanceof HttpApiV2ProxyRequestContext requestContext)) {
            throw new IllegalStateException(
                    "Missing API Gateway request context; is this route behind the JWT authorizer?");
        }
        HttpApiV2AuthorizerMap authorizer = requestContext.getAuthorizer();
        if (authorizer == null || !authorizer.isJwt()) {
            throw new IllegalStateException("Request was not authorized by the JWT authorizer");
        }
        HttpApiV2JwtAuthorizer jwt = authorizer.getJwtAuthorizer();
        String sub = jwt.getClaims().get("sub");
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("JWT claims did not include sub");
        }
        return sub;
    }
}
