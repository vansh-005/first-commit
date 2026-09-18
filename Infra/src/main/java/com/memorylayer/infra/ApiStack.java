package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
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
 * Phase 1: a single public health-check route. No auth, no persistence, no downstream
 * AWS services — those arrive in later phases per Docs/TASKS.md.
 *
 * <p>The Lambda integration/route are wired with the stable L1 constructs
 * ({@code CfnIntegration}/{@code CfnRoute}) rather than the {@code HttpLambdaIntegration} L2:
 * that L2 only ships in the long-abandoned {@code apigatewayv2-integrations-alpha} module
 * (last published for aws-cdk-lib 2.114.1), and it was compiled against a pre-stabilization
 * copy of {@code apigatewayv2}'s core types — incompatible with the stabilized {@code HttpApi}
 * now bundled in aws-cdk-lib, so the two cannot be used together in Java.
 */
public class ApiStack extends Stack {

    private final String apiEndpoint;

    public ApiStack(final Construct scope, final String id, final StackProps props) {
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
                        // TEMPORARY (Phase 1 only): /health is the only public route, so a
                        // permissive origin is acceptable. Tighten this to the deployed
                        // Amplify origin + localhost once authenticated routes are added.
                        .allowOrigins(List.of("*"))
                        .allowMethods(List.of(CorsHttpMethod.GET))
                        .allowHeaders(List.of("Content-Type"))
                        .build())
                .build();

        CfnIntegration integration = CfnIntegration.Builder.create(this, "HealthIntegration")
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

        liveAlias.addPermission("ApiGatewayInvoke", Permission.builder()
                .principal(new ServicePrincipal("apigateway.amazonaws.com"))
                .action("lambda:InvokeFunction")
                .sourceArn(httpApi.arnForExecuteApi("GET", "/api/v1/health", "*"))
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
