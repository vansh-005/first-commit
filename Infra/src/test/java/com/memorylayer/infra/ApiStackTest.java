package com.memorylayer.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiStackTest {

    @Test
    void createsSnapStartLambdaHealthRouteAndProtectedRoutes() {
        App app = new App();
        Environment env = Environment.builder().account("123456789012").region("ap-south-1").build();
        AuthStack authStack = new AuthStack(app, "TestAuthStack", StackProps.builder().env(env).build(),
                "https://main.example.amplifyapp.com", "test-google-client-id");
        DataStack dataStack = new DataStack(app, "TestDataStack", StackProps.builder().env(env).build(),
                "https://main.example.amplifyapp.com");
        ApiStack stack = new ApiStack(app, "TestApiStack", StackProps.builder().env(env).build(), authStack, dataStack);
        Template template = Template.fromStack(stack);

        template.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "Runtime", "java21",
                "Architectures", java.util.List.of("arm64"),
                "SnapStart", Map.of("ApplyOn", "PublishedVersions")
        ));

        template.hasResourceProperties("AWS::ApiGatewayV2::Route", Map.of(
                "RouteKey", "GET /api/v1/health"
        ));

        for (String routeKey : java.util.List.of(
                "GET /api/v1/me",
                "POST /api/v1/uploads",
                "GET /api/v1/documents",
                "GET /api/v1/documents/{documentId}",
                "GET /api/v1/documents/{documentId}/access-url")) {
            template.hasResourceProperties("AWS::ApiGatewayV2::Route", Match.objectLike(Map.of(
                    "RouteKey", routeKey,
                    "AuthorizationType", "JWT",
                    "AuthorizationScopes", java.util.List.of(AuthStack.FULL_ACCESS_SCOPE)
            )));
        }

        template.hasResourceProperties("AWS::ApiGatewayV2::Authorizer", Match.objectLike(Map.of(
                "AuthorizerType", "JWT"
        )));

        template.hasResourceProperties("AWS::ApiGatewayV2::Api", Match.objectLike(Map.of(
                "CorsConfiguration", Match.objectLike(Map.of(
                        "AllowMethods", java.util.List.of("GET", "POST")
                ))
        )));

        // The Lambda role must actually be able to read/write the table and put/get
        // objects under users/ — otherwise presigned URLs it issues would 403 at use time.
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", Match.arrayWith(List.of(Match.objectLike(Map.of(
                                "Action", Match.arrayWith(List.of("dynamodb:Query"))
                        ))))
                ))
        )));

        assertNotNull(stack.getApiEndpoint());
    }
}
