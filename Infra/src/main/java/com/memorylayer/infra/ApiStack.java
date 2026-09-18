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
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SnapStartConf;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.List;

/**
 * Phase 1 added a single public health-check route. Phase 2 added a Cognito JWT authorizer
 * and a protected diagnostic route (`/api/v1/me`). Phase 3 adds the upload/document routes
 * from Docs/API.md §10/15-17, all behind the same authorizer + `memory-api/access` scope.
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
    private final HttpApi httpApi;
    private final CfnIntegration integration;
    private final CfnAuthorizer authorizer;

    public ApiStack(final Construct scope, final String id, final StackProps props,
                     final AuthStack authStack, final DataStack dataStack) {
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
                .environment(java.util.Map.of(
                        "TABLE_NAME", dataStack.getTable().getTableName(),
                        "UPLOADS_BUCKET_NAME", dataStack.getUploadsBucket().getBucketName()))
                // SnapStart only restores from a published version, never $LATEST.
                .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
                .build();

        dataStack.getTable().grantReadWriteData(apiFunction);
        // Scoped to the users/ prefix — defense in depth on top of the application code,
        // which is the only thing that ever decides the actual key within that prefix.
        dataStack.getUploadsBucket().grantPut(apiFunction, "users/*");
        dataStack.getUploadsBucket().grantRead(apiFunction, "users/*");

        Alias liveAlias = Alias.Builder.create(this, "ApiFunctionLiveAlias")
                .aliasName("live")
                .version(apiFunction.getCurrentVersion())
                .build();

        this.httpApi = HttpApi.Builder.create(this, "HttpApi")
                .apiName("memory-layer-api")
                .corsPreflight(CorsPreflightOptions.builder()
                        // Tightened in Phase 2 now that authenticated routes exist and real
                        // origins are known. Only the deployed Amplify origin + local dev.
                        .allowOrigins(List.of(InfraApp.AMPLIFY_ORIGIN, "http://localhost:5173"))
                        .allowMethods(List.of(CorsHttpMethod.GET, CorsHttpMethod.POST))
                        .allowHeaders(List.of("Content-Type", "Authorization"))
                        .build())
                .build();

        // Single integration reused by every route: same Lambda handles all of them.
        this.integration = CfnIntegration.Builder.create(this, "ApiIntegration")
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

        this.authorizer = CfnAuthorizer.Builder.create(this, "JwtAuthorizer")
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
        addProtectedRoute("Me", "GET", "/api/v1/me");

        // Phase 3: upload initialization and file library routes (Docs/API.md §10, §15-17).
        addProtectedRoute("Uploads", "POST", "/api/v1/uploads");
        addProtectedRoute("DocumentsList", "GET", "/api/v1/documents");
        addProtectedRoute("DocumentGet", "GET", "/api/v1/documents/{documentId}");
        addProtectedRoute("DocumentAccessUrl", "GET", "/api/v1/documents/{documentId}/access-url");

        liveAlias.addPermission("ApiGatewayInvokeHealth", Permission.builder()
                .principal(new ServicePrincipal("apigateway.amazonaws.com"))
                .action("lambda:InvokeFunction")
                .sourceArn(httpApi.arnForExecuteApi("GET", "/api/v1/health", "*"))
                .build());

        // One wildcard statement for every protected route rather than one per route: API
        // Gateway (and the authorizer/scope config above) is still the actual boundary on
        // which routes exist and who may call them — this permission only governs which
        // API can invoke the Lambda at all.
        liveAlias.addPermission("ApiGatewayInvokeProtected", Permission.builder()
                .principal(new ServicePrincipal("apigateway.amazonaws.com"))
                .action("lambda:InvokeFunction")
                .sourceArn(httpApi.arnForExecuteApi("*", "/api/v1/*", "*"))
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

    private void addProtectedRoute(String idPrefix, String method, String path) {
        CfnRoute.Builder.create(this, idPrefix + "Route")
                .apiId(httpApi.getHttpApiId())
                .routeKey(method + " " + path)
                .target("integrations/" + integration.getRef())
                .authorizationType("JWT")
                .authorizerId(authorizer.getRef())
                // API Gateway rejects the request with 403 before invoking the Lambda if the
                // access token's `scope` claim doesn't contain this — no backend check needed.
                .authorizationScopes(List.of(AuthStack.FULL_ACCESS_SCOPE))
                .build();
    }

    /** Base API URL, consumed by FrontendStack as the frontend's VITE_API_BASE_URL. */
    public String getApiEndpoint() {
        return apiEndpoint;
    }
}
