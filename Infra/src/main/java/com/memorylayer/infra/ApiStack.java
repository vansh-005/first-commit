package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.CfnAuthorizer;
import software.amazon.awscdk.services.apigatewayv2.CfnIntegration;
import software.amazon.awscdk.services.apigatewayv2.CfnRoute;
import software.amazon.awscdk.services.apigatewayv2.CorsHttpMethod;
import software.amazon.awscdk.services.apigatewayv2.CorsPreflightOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SnapStartConf;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

import java.util.List;

/**
 * Phase 1 added a single public health-check route. Phase 2 adds a Cognito JWT authorizer
 * and one protected diagnostic route (`/api/v1/me`) proving the authorizer/scope wiring
 * works; real protected business endpoints arrive in later phases per Docs/TASKS.md.
 *
 * <p>The Lambda integration/route are wired with the stable L1 constructs
 * ({@code CfnIntegration}/{@code CfnRoute}) rather than the {@code HttpLambdaIntegration} L2:
 * that L2 only ships in the long-abandoned {@code apigatewayv2-integrations-alpha} module
 * (last published for aws-cdk-lib 2.114.1), and it was compiled against a pre-stabilization
 * copy of {@code apigatewayv2}'s core types — incompatible with the stabilized {@code HttpApi}
 * now bundled in aws-cdk-lib, so the two cannot be used together in Java. The same reasoning
 * applies to the JWT authorizer, wired here with the stable L1 {@code CfnAuthorizer}.
 */
public class ApiStack extends Stack {

    private final String apiEndpoint;

    public ApiStack(final Construct scope, final String id, final StackProps props, final AuthStack authStack) {
        super(scope, id, props);

        LogGroup logGroup = LogGroup.Builder.create(this, "ApiFunctionLogGroup")
                .logGroupName("/aws/lambda/memory-layer-api")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Function apiFunction = Function.Builder.create(this, "ApiFunction")
                .functionName("memory-layer-api")
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.ARM_64)
                .handler("com.memorylayer.api.LambdaHandler::handleRequest")
                .code(Code.fromAsset("../Backend/target/backend.jar"))
                .memorySize(1024)
                .timeout(Duration.seconds(10))
                .logGroup(logGroup)
                // SnapStart only restores from a published version, never $LATEST.
                .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
                .build();

        Alias liveAlias = Alias.Builder.create(this, "ApiFunctionLiveAlias")
                .aliasName("live")
                .version(apiFunction.getCurrentVersion())
                .build();

        HttpApi httpApi = HttpApi.Builder.create(this, "HttpApi")
                .apiName("memory-layer-api")
                .corsPreflight(CorsPreflightOptions.builder()
                        // Tightened in Phase 2 now that authenticated routes exist and real
                        // origins are known. Only the deployed Amplify origin + local dev.
                        .allowOrigins(List.of(InfraApp.AMPLIFY_ORIGIN, "http://localhost:5173"))
                        .allowMethods(List.of(CorsHttpMethod.GET))
                        .allowHeaders(List.of("Content-Type", "Authorization"))
                        .build())
                .build();

        // Single integration reused by every route: same Lambda handles all of them.
        CfnIntegration integration = CfnIntegration.Builder.create(this, "ApiIntegration")
                .apiId(httpApi.getHttpApiId())
                .integrationType("AWS_PROXY")
                .integrationUri(liveAlias.getFunctionArn())
                .payloadFormatVersion("2.0")
                .build();

        CfnRoute.Builder.create(this, "HealthRoute")
                .apiId(httpApi.getHttpApiId())
                .routeKey("GET /api/v1/health")
                .target("integrations/" + integration.getRef())
                .build();

        CfnAuthorizer authorizer = CfnAuthorizer.Builder.create(this, "JwtAuthorizer")
                .apiId(httpApi.getHttpApiId())
                .authorizerType("JWT")
                .identitySource(List.of("$request.header.Authorization"))
                .name("CognitoJwtAuthorizer")
                .jwtConfiguration(CfnAuthorizer.JWTConfigurationProperty.builder()
                        .audience(List.of(authStack.getUserPoolClientId()))
                        .issuer(authStack.getIssuer())
                        .build())
                .build();

        // Internal Phase 2 diagnostic route only (see Docs/API.md) — proves the JWT
        // authorizer + custom scope wiring works. Not a permanent product endpoint.
        CfnRoute.Builder.create(this, "MeRoute")
                .apiId(httpApi.getHttpApiId())
                .routeKey("GET /api/v1/me")
                .target("integrations/" + integration.getRef())
                .authorizationType("JWT")
                .authorizerId(authorizer.getRef())
                // API Gateway rejects the request with 403 before invoking the Lambda if the
                // access token's `scope` claim doesn't contain this — no backend check needed.
                .authorizationScopes(List.of(AuthStack.FULL_ACCESS_SCOPE))
                .build();

        liveAlias.addPermission("ApiGatewayInvoke", Permission.builder()
                .principal(new ServicePrincipal("apigateway.amazonaws.com"))
                .action("lambda:InvokeFunction")
                .sourceArn(httpApi.arnForExecuteApi("GET", "/api/v1/health", "*"))
                .build());

        liveAlias.addPermission("ApiGatewayInvokeMe", Permission.builder()
                .principal(new ServicePrincipal("apigateway.amazonaws.com"))
                .action("lambda:InvokeFunction")
                .sourceArn(httpApi.arnForExecuteApi("GET", "/api/v1/me", "*"))
                .build());

        this.apiEndpoint = httpApi.getApiEndpoint();

        CfnOutput.Builder.create(this, "ApiEndpoint")
                .value(apiEndpoint)
                .description("Base URL of the deployed HTTP API")
                .build();

        CfnOutput.Builder.create(this, "HealthUrl")
                .value(apiEndpoint + "/api/v1/health")
                .description("Deployed GET /api/v1/health URL")
                .build();
    }

    /** Base API URL, consumed by FrontendStack as the frontend's VITE_API_BASE_URL. */
    public String getApiEndpoint() {
        return apiEndpoint;
    }
}
