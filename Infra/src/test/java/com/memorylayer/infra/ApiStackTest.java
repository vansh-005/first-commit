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
        IngestionStack ingestionStack = new IngestionStack(app, "TestIngestionStack",
                StackProps.builder().env(env).build(), dataStack);
        ApiStack stack = new ApiStack(app, "TestApiStack", StackProps.builder().env(env).build(),
                authStack, dataStack, ingestionStack);
        Template template = Template.fromStack(stack);

        template.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "Runtime", "java21",
                "Architectures", java.util.List.of("arm64"),
                "SnapStart", Map.of("ApplyOn", "PublishedVersions")
        ));

        // Timeout invariant: the Lambda (28s) must terminate before the HTTP API integration ceiling (30s).
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(Map.of("Timeout", 28)));
        template.hasResourceProperties("AWS::ApiGatewayV2::Integration", Match.objectLike(Map.of("TimeoutInMillis", 30000)));

        template.hasResourceProperties("AWS::ApiGatewayV2::Route", Map.of(
                "RouteKey", "GET /api/v1/health"
        ));

        for (String routeKey : java.util.List.of(
                "GET /api/v1/me",
                "POST /api/v1/uploads",
                "GET /api/v1/documents",
                "GET /api/v1/documents/{documentId}",
                "GET /api/v1/documents/{documentId}/access-url",
                "POST /api/v1/search",
                "POST /api/v1/ask")) {
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

        // Phase 5: the Lambda role must be able to call Retrieve, scoped to the Knowledge
        // Base ARN — otherwise /search would 403 downstream at call time. The IAM action
        // namespace is "bedrock:", not "bedrock-agent-runtime:" (the SDK/client name) —
        // confirmed live via the exact AccessDeniedException wording.
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", Match.arrayWith(List.of(Match.objectLike(Map.of(
                                "Action", "bedrock:Retrieve"
                        ))))
                ))
        )));

        // Phase 6: the Lambda role must be able to call RetrieveAndGenerate, as its own
        // statement scoped to Resource: "*" — not folded into the Retrieve statement above and
        // not scoped to the Knowledge Base ARN, per current AWS Knowledge Bases IAM
        // documentation (the call also invokes the configured model/inference profile, a
        // separate resource from the Knowledge Base itself). GetInferenceProfile/InvokeModel*
        // were added after a live AccessDeniedException during Phase 6 verification —
        // RetrieveAndGenerate against a cross-region inference profile also resolves/invokes
        // the profile itself.
        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", Match.arrayWith(List.of(Match.objectLike(Map.of(
                                "Action", Match.arrayWith(List.of("bedrock:RetrieveAndGenerate", "bedrock:GetInferenceProfile")),
                                "Resource", "*"
                        ))))
                ))
        )));

        assertNotNull(stack.getApiEndpoint());
    }
}
