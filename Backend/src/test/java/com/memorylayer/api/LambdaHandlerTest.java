package com.memorylayer.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Exercises the real Lambda entrypoint with a raw HTTP API v2 event JSON, exactly as API
 * Gateway sends it. A MockMvc-based controller test that injects an already-typed
 * HttpApiV2ProxyRequestContext (see MeControllerTest) bypasses JSON deserialization
 * entirely and cannot catch a bug in that deserialization — which is exactly what caused a
 * production 500 on the real, deployed authorizer-protected route.
 */
class LambdaHandlerTest {

    private static final String ME_EVENT_JSON = """
            {
              "version": "2.0",
              "routeKey": "GET /api/v1/me",
              "rawPath": "/api/v1/me",
              "rawQueryString": "",
              "headers": { "authorization": "Bearer test-token" },
              "requestContext": {
                "accountId": "123456789012",
                "apiId": "testapi",
                "domainName": "testapi.execute-api.ap-south-1.amazonaws.com",
                "domainPrefix": "testapi",
                "http": {
                  "method": "GET",
                  "path": "/api/v1/me",
                  "protocol": "HTTP/1.1",
                  "sourceIp": "127.0.0.1",
                  "userAgent": "junit"
                },
                "requestId": "test-request-id",
                "routeKey": "GET /api/v1/me",
                "stage": "$default",
                "time": "18/Sep/2026:00:00:00 +0000",
                "timeEpoch": 1758153600000,
                "authorizer": {
                  "jwt": {
                    "claims": {
                      "sub": "test-user-123",
                      "scope": "openid email profile memory-api/access"
                    },
                    "scopes": null
                  }
                }
              },
              "isBase64Encoded": false
            }
            """;

    @Test
    void meRouteExtractsSubFromRealHttpApiV2AuthorizedEvent() throws Exception {
        LambdaHandler lambdaHandler = new LambdaHandler();
        Context context = mock(Context.class);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(ME_EVENT_JSON.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        lambdaHandler.handleRequest(inputStream, outputStream, context);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode response = mapper.readTree(outputStream.toByteArray());
        assertEquals(200, response.get("statusCode").asInt());

        JsonNode body = mapper.readTree(response.get("body").asText());
        assertEquals("test-user-123", body.get("userId").asText());
    }
}
