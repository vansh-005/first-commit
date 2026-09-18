package com.memorylayer.api.controller;

import com.amazonaws.serverless.proxy.RequestReader;
import com.amazonaws.serverless.proxy.model.HttpApiV2AuthorizerMap;
import com.amazonaws.serverless.proxy.model.HttpApiV2JwtAuthorizer;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.cloud.function.serverless.web.ServerlessAutoConfiguration")
@AutoConfigureMockMvc
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meReturnsSubFromValidatedJwtClaims() throws Exception {
        HttpApiV2JwtAuthorizer jwt = new HttpApiV2JwtAuthorizer();
        jwt.setClaims(Map.of("sub", "test-user-123", "scope", "openid memory-api/access"));

        HttpApiV2AuthorizerMap authorizerMap = new HttpApiV2AuthorizerMap();
        authorizerMap.putJwtAuthorizer(jwt);

        HttpApiV2ProxyRequestContext requestContext = new HttpApiV2ProxyRequestContext();
        requestContext.setAuthorizer(authorizerMap);

        mockMvc.perform(get("/api/v1/me")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, requestContext))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("test-user-123"));
    }

    @Test
    void meWithoutAuthorizerContextFailsClosed() throws Exception {
        // Simulates the Lambda being reached without API Gateway's JWT authorizer context —
        // should never happen in practice, but must fail closed, not leak a null userId.
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isInternalServerError());
    }
}
