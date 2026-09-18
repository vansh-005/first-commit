package com.memorylayer.api;

import com.amazonaws.serverless.exceptions.ContainerInitializationException;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.model.HttpApiV2ProxyRequest;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lambda entrypoint for API Gateway HTTP API (payload format 2.0). SnapStart reuses this
 * static initializer's Spring context snapshot, so no per-invocation setup happens here.
 *
 * <p>Uses the stream-based entrypoint ({@code proxyStream}) rather than the POJO
 * {@code RequestHandler<HttpApiV2ProxyRequest, ...>} variant. AWS Lambda's own default event
 * deserialization (used to build the POJO argument for a {@code RequestHandler}) does not
 * honor aws-serverless-java-container's {@code @JsonDeserialize} annotation on
 * {@code HttpApiV2AuthorizerMap}: the JWT authorizer's claims land as a raw
 * {@code LinkedHashMap} instead of the typed {@code HttpApiV2JwtAuthorizer}, causing a
 * {@code ClassCastException} in any code that reads authorizer claims (e.g.
 * {@link com.memorylayer.api.security.AuthenticatedUserResolver}). {@code proxyStream} parses
 * the raw event with the container library's own configured {@code ObjectMapper}
 * ({@link com.amazonaws.serverless.proxy.internal.LambdaContainerHandler#getObjectMapper()}),
 * which correctly applies that annotation.
 */
public class LambdaHandler implements RequestStreamHandler {

    private static final SpringBootLambdaContainerHandler<HttpApiV2ProxyRequest, AwsProxyResponse> handler;

    static {
        try {
            handler = SpringBootLambdaContainerHandler.getHttpApiV2ProxyHandler(MemoryLayerApplication.class);
        } catch (ContainerInitializationException e) {
            throw new RuntimeException("Could not initialize Spring Boot application", e);
        }
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        handler.proxyStream(inputStream, outputStream, context);
    }
}
