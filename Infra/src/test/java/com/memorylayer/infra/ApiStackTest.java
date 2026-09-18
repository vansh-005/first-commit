package com.memorylayer.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiStackTest {

    @Test
    void createsSnapStartLambdaAndHealthRoute() {
        App app = new App();
        Environment env = Environment.builder().account("123456789012").region("ap-south-1").build();
        ApiStack stack = new ApiStack(app, "TestApiStack", StackProps.builder().env(env).build());
        Template template = Template.fromStack(stack);

        template.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "Runtime", "java21",
                "Architectures", java.util.List.of("arm64"),
                "SnapStart", Map.of("ApplyOn", "PublishedVersions")
        ));

        template.hasResourceProperties("AWS::ApiGatewayV2::Route", Map.of(
                "RouteKey", "GET /api/v1/health"
        ));

        template.hasResourceProperties("AWS::ApiGatewayV2::Api", Match.objectLike(Map.of(
                "CorsConfiguration", Match.objectLike(Map.of(
                        "AllowMethods", java.util.List.of("GET")
                ))
        )));

        assertNotNull(stack.getApiEndpoint());
    }
}
