package com.memorylayer.api.security;

import com.amazonaws.serverless.proxy.model.HttpApiV2AuthorizerMap;
import com.amazonaws.serverless.proxy.model.HttpApiV2JwtAuthorizer;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequestContext;

import java.util.Map;

/** Builds the same typed API Gateway JWT authorizer context aws-serverless-java-container
 * attaches to a real request, for MockMvc tests of protected routes. */
public final class AuthorizedRequestSupport {

    private AuthorizedRequestSupport() {
    }

    public static HttpApiV2ProxyRequestContext contextForSub(String sub) {
        HttpApiV2JwtAuthorizer jwt = new HttpApiV2JwtAuthorizer();
        jwt.setClaims(Map.of("sub", sub, "scope", "openid email profile memory-api/access"));

        HttpApiV2AuthorizerMap authorizerMap = new HttpApiV2AuthorizerMap();
        authorizerMap.putJwtAuthorizer(jwt);

        HttpApiV2ProxyRequestContext requestContext = new HttpApiV2ProxyRequestContext();
        requestContext.setAuthorizer(authorizerMap);
        return requestContext;
    }
}
