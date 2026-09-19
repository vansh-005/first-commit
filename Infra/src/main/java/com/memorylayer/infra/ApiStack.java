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
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
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
 * Phase 5 adds semantic search (`/api/v1/search`, Docs/API.md §18), which needs a reference to
 * the Knowledge Base created in {@code IngestionStack} — hence the added constructor
 * parameter and {@code InfraApp} now constructing that stack before this one. Phase 6 adds
 * grounded Q&A (`/api/v1/ask`, Docs/API.md §20) on the same Knowledge Base via
 * {@code RetrieveAndGenerate}.
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
    private Function apiFunction;
    private final HttpApi httpApi;
    private final CfnIntegration integration;
    private final CfnAuthorizer authorizer;

    public ApiStack(final Construct scope, final String id, final StackProps props,
                     final AuthStack authStack, final DataStack dataStack, final IngestionStack ingestionStack) {
        super(scope, id, props);

        // Phase 6: the generation model for POST /api/v1/ask. Amazon Nova Lite via the APAC
        // cross-region inference profile — confirmed live during Phase 6 planning: Anthropic
        // models on this account require a separate, manual "model use case details" form
        // (account-level, outside CDK's control, and observed to fail intermittently even when
        // filled out), while Nova Lite has no such gate and produced accurate grounded answers
        // in live testing against the deployed Knowledge Base. Same APAC cross-region pattern
        // Phase 4 found required for BDA invocation from ap-south-1.
        String askModelArn = "arn:aws:bedrock:" + this.getRegion() + ":" + this.getAccount()
                + ":inference-profile/apac.amazon.nova-lite-v1:0";

        LogGroup logGroup = LogGroup.Builder.create(this, "ApiFunctionLogGroup")
                .logGroupName("/aws/lambda/memory-layer-api")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        this.apiFunction = Function.Builder.create(this, "ApiFunction")
                .functionName("memory-layer-api")
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.ARM_64)
                .handler("com.memorylayer.api.LambdaHandler::handleRequest")
                .code(Code.fromAsset("../Backend/target/backend.jar"))
                .memorySize(1024)
                // Ask can make two Bedrock round-trips (preflight Retrieve + generation, plus one recovery retry),
                // measured at ~9-15s for a retrying turn - a 10s ceiling timed those out. 28s leaves the Lambda
                // terminating just BEFORE the 30s HTTP API integration ceiling below, so the gateway never has to
                // cut the connection first.
                .timeout(Duration.seconds(28))
                .logGroup(logGroup)
                .environment(java.util.Map.of(
                        "TABLE_NAME", dataStack.getTable().getTableName(),
                        "UPLOADS_BUCKET_NAME", dataStack.getUploadsBucket().getBucketName(),
                        "KNOWLEDGE_BASE_ID", ingestionStack.getKnowledgeBaseId(),
                        "ASK_MODEL_ARN", askModelArn))
                // SnapStart only restores from a published version, never $LATEST.
                .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
                .build();

        dataStack.getTable().grantReadWriteData(apiFunction);
        // Scoped to the users/ prefix — defense in depth on top of the application code,
        // which is the only thing that ever decides the actual key within that prefix.
        dataStack.getUploadsBucket().grantPut(apiFunction, "users/*");
        dataStack.getUploadsBucket().grantRead(apiFunction, "users/*");
        // Phase 5: POST /api/v1/search's Retrieve calls. Confirmed against the bare Knowledge
        // Base ARN (not a sub-resource), matching the Phase 4 StartIngestionJob/GetIngestionJob
        // precedent. The IAM action namespace is "bedrock:", not "bedrock-agent-runtime:" (the
        // SDK/client name) — confirmed live via the exact AccessDeniedException wording after
        // deploying with the SDK-namespaced action, which only ever names "bedrock:Retrieve".
        apiFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("bedrock:Retrieve"))
                .resources(List.of(ingestionStack.getKnowledgeBaseArn()))
                .build());
        // Phase 6: POST /api/v1/ask's RetrieveAndGenerate calls. Kept as a separate statement
        // from Retrieve above, deliberately not scoped to the Knowledge Base ARN — current AWS
        // documentation for Bedrock Knowledge Bases requires bedrock:RetrieveAndGenerate to be
        // granted against Resource: "*", since the call also invokes the configured foundation
        // model/inference profile (a separate resource from the Knowledge Base itself).
        //
        // bedrock:GetInferenceProfile and bedrock:InvokeModel*/were added after a live
        // AccessDeniedException ("Not authorized to call GetInferenceProfile for
        // arn:aws:bedrock:ap-south-1:<account>:inference-profile/apac.amazon.nova-lite-v1:0")
        // during Phase 6 verification: RetrieveAndGenerate against a cross-region inference
        // profile (ASK_MODEL_ARN) also resolves/invokes the profile itself, which
        // bedrock:RetrieveAndGenerate alone does not cover.
        apiFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "bedrock:RetrieveAndGenerate",
                        "bedrock:GetInferenceProfile",
                        "bedrock:InvokeModel",
                        "bedrock:InvokeModelWithResponseStream"))
                .resources(List.of("*"))
                .build());

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
                // Explicit (it is also the HTTP API maximum): the function timeout above must stay below this.
                .timeoutInMillis(30_000)
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

        // Phase 5: semantic search (Docs/API.md §18) — Retrieve only, never RetrieveAndGenerate.
        addProtectedRoute("Search", "POST", "/api/v1/search");

        // Phase 6: grounded Q&A (Docs/API.md §20) — RetrieveAndGenerate.
        addProtectedRoute("Ask", "POST", "/api/v1/ask");

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

    /** Phase 7: consumed by AlarmsStack. */
    public Function getApiFunction() {
        return apiFunction;
    }
}
