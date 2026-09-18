package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.bedrock.CfnDataSource;
import software.amazon.awscdk.services.bedrock.CfnKnowledgeBase;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.Schedule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.Policy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.iam.ServicePrincipalOpts;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSourceProps;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3vectors.CfnIndex;
import software.amazon.awscdk.services.s3vectors.CfnVectorBucket;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 4: asynchronous ingestion. Docs/ARCHITECTURE.md §7, as amended by the Phase 4
 * compatibility spike (staging-prefix routing, dedicated supplemental bucket, generous SQS
 * retry budget for Bedrock's single-concurrent-ingestion-job constraint — see the spike
 * report for the exact errors that drove each of those three decisions).
 *
 * <p>Resource flow:
 * <pre>
 * S3 users/ ObjectCreated -> SQS -> Ingestion Coordinator Lambda
 *     -> copies source object into kb/multimodal/ or kb/text/, writes .metadata.json sidecar
 *     -> StartIngestionJob on the matching data source
 *     -> DynamoDB SYSTEM#INGESTION job record
 *
 * EventBridge (rate 1 min) -> Status Reconciler Lambda -> GetIngestionJob -> document READY/FAILED
 * </pre>
 */
public class IngestionStack extends Stack {

    public IngestionStack(final Construct scope, final String id, final StackProps props, final DataStack dataStack) {
        super(scope, id, props);

        Bucket uploadsBucket = dataStack.getUploadsBucket();

        // --- Supplemental storage (multimodal/BDA-extracted media) -------------------------
        // Per the spike: must be a dedicated bucket at its ROOT (no prefix), and this field on
        // the Knowledge Base is immutable once created — get it right on the very first
        // deploy, since changing it later means destroying and recreating the KB.
        Bucket supplementalBucket = Bucket.Builder.create(this, "SupplementalBucket")
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        // --- S3 Vectors vector store ---------------------------------------------------------
        CfnVectorBucket vectorBucket = CfnVectorBucket.Builder.create(this, "VectorBucket").build();

        CfnIndex vectorIndex = CfnIndex.Builder.create(this, "VectorIndex")
                .vectorBucketArn(vectorBucket.getAttrVectorBucketArn())
                .indexName("memory-layer-index")
                .dataType("float32")
                // Titan Text Embeddings V2's output dimension.
                .dimension(1024)
                .distanceMetric("cosine")
                // Keeps Bedrock's own chunk-text/metadata fields out of the 2 KB per-vector
                // filterable-metadata limit — AWS's documented recommendation for a Bedrock KB
                // backed by S3 Vectors.
                .metadataConfiguration(CfnIndex.MetadataConfigurationProperty.builder()
                        .nonFilterableMetadataKeys(List.of("AMAZON_BEDROCK_METADATA", "AMAZON_BEDROCK_TEXT"))
                        .build())
                .build();

        // --- Knowledge Base execution role ----------------------------------------------------
        Role kbRole = Role.Builder.create(this, "KnowledgeBaseRole")
                .assumedBy(new ServicePrincipal("bedrock.amazonaws.com", ServicePrincipalOpts.builder()
                        .conditions(Map.of("StringEquals", Map.of("aws:SourceAccount", this.getAccount())))
                        .build()))
                .build();

        List<PolicyStatement> kbPolicyStatements = new ArrayList<>();

        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("FoundationModel")
                .effect(Effect.ALLOW)
                .actions(List.of("bedrock:InvokeModel"))
                .resources(List.of("arn:aws:bedrock:" + this.getRegion() + "::foundation-model/amazon.titan-embed-text-v2:0"))
                .build());

        // Scoped to kb/* — the staging prefixes, never users/ — per the Phase 4 amendment:
        // each data source only ever reads what the coordinator has already routed there.
        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("S3DataSourceAccess")
                .effect(Effect.ALLOW)
                .actions(List.of("s3:GetObject"))
                .resources(List.of(uploadsBucket.arnForObjects("kb/*")))
                .build());
        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("S3DataSourceList")
                .effect(Effect.ALLOW)
                .actions(List.of("s3:ListBucket"))
                .resources(List.of(uploadsBucket.getBucketArn()))
                .conditions(Map.of("StringLike", Map.of("s3:prefix", List.of("kb/*"))))
                .build());

        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("S3Vectors")
                .effect(Effect.ALLOW)
                .actions(List.of(
                        "s3vectors:GetIndex",
                        "s3vectors:PutVectors",
                        "s3vectors:GetVectors",
                        "s3vectors:ListVectors",
                        "s3vectors:DeleteVectors",
                        "s3vectors:QueryVectors"))
                .resources(List.of(vectorBucket.getAttrVectorBucketArn(), vectorIndex.getAttrIndexArn()))
                .build());

        // Deployment-time bug found and fixed: BDA's actual cross-region invocation profile
        // for a Knowledge Base in ap-south-1 lives in ap-northeast-1 under THIS account (not
        // the "aws"-owned same-region profile AWS's documented example uses) — confirmed by
        // the real AccessDeniedException naming
        // "arn:aws:bedrock:ap-northeast-1:<account>:data-automation-profile/apac.data-automation-v1"
        // as the checked resource. Wildcarding the region segment covers this without
        // hardcoding the specific APAC profile name/region, in case it changes.
        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("BDAInvoke")
                .effect(Effect.ALLOW)
                .actions(List.of("bedrock:InvokeDataAutomationAsync"))
                .resources(List.of(
                        "arn:aws:bedrock:" + this.getRegion() + ":aws:data-automation-project/public-rag-default",
                        "arn:aws:bedrock:" + this.getRegion() + "::data-automation-profile/*",
                        "arn:aws:bedrock:*:" + this.getAccount() + ":data-automation-profile/*"))
                .build());
        // Same cross-region reasoning as BDAInvoke above — the invocation this status check
        // polls may itself be running in the cross-region APAC profile's region.
        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("BDAGetStatus")
                .effect(Effect.ALLOW)
                .actions(List.of("bedrock:GetDataAutomationStatus"))
                .resources(List.of(
                        "arn:aws:bedrock:" + this.getRegion() + "::data-automation-invocation/*",
                        "arn:aws:bedrock:*:" + this.getAccount() + ":data-automation-invocation/*"))
                .build());

        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("S3SupplementalStorageList")
                .effect(Effect.ALLOW)
                .actions(List.of("s3:ListBucket"))
                .resources(List.of(supplementalBucket.getBucketArn()))
                .build());
        kbPolicyStatements.add(PolicyStatement.Builder.create()
                .sid("S3SupplementalStorageObjects")
                .effect(Effect.ALLOW)
                // DeleteObject is required — omitting it fails KB creation with a misleading
                // "write access" validation error (confirmed in the Phase 4 spike).
                .actions(List.of("s3:GetObject", "s3:PutObject", "s3:DeleteObject"))
                .resources(List.of(supplementalBucket.arnForObjects("*")))
                .build());

        // A single explicit Policy resource (rather than several role.addToPolicy() calls)
        // so the KnowledgeBase below can declare an explicit CloudFormation dependency on it.
        // Deployment-time bug found and fixed: the KB's `roleArn` property only makes
        // CloudFormation depend on the ROLE resource, not on the separate AWS::IAM::Policy
        // resource that actually attaches these permission statements — so CloudFormation was
        // free to create the KnowledgeBase before the policy was attached, deterministically
        // failing with "not authorized to perform: s3vectors:QueryVectors" on every attempt
        // (confirmed by two consecutive full stack rollbacks with fresh IAM roles each time,
        // not resolved by waiting — ruling out plain IAM propagation delay).
        Policy kbPolicy = Policy.Builder.create(this, "KnowledgeBaseRolePolicy")
                .statements(kbPolicyStatements)
                .roles(List.of(kbRole))
                .build();

        // --- Knowledge Base --------------------------------------------------------------------
        CfnKnowledgeBase knowledgeBase = CfnKnowledgeBase.Builder.create(this, "KnowledgeBase")
                .name("memory-layer-kb")
                .roleArn(kbRole.getRoleArn())
                .knowledgeBaseConfiguration(CfnKnowledgeBase.KnowledgeBaseConfigurationProperty.builder()
                        .type("VECTOR")
                        .vectorKnowledgeBaseConfiguration(CfnKnowledgeBase.VectorKnowledgeBaseConfigurationProperty.builder()
                                .embeddingModelArn("arn:aws:bedrock:" + this.getRegion()
                                        + "::foundation-model/amazon.titan-embed-text-v2:0")
                                // Immutable after creation — must be present on the very first
                                // deploy if BDA/multimodal parsing will ever be used.
                                .supplementalDataStorageConfiguration(CfnKnowledgeBase.SupplementalDataStorageConfigurationProperty.builder()
                                        .supplementalDataStorageLocations(List.of(
                                                CfnKnowledgeBase.SupplementalDataStorageLocationProperty.builder()
                                                        .supplementalDataStorageLocationType("S3")
                                                        .s3Location(CfnKnowledgeBase.S3LocationProperty.builder()
                                                                .uri("s3://" + supplementalBucket.getBucketName())
                                                                .build())
                                                        .build()))
                                        .build())
                                .build())
                        .build())
                .storageConfiguration(CfnKnowledgeBase.StorageConfigurationProperty.builder()
                        .type("S3_VECTORS")
                        .s3VectorsConfiguration(CfnKnowledgeBase.S3VectorsConfigurationProperty.builder()
                                .vectorBucketArn(vectorBucket.getAttrVectorBucketArn())
                                .indexArn(vectorIndex.getAttrIndexArn())
                                .build())
                        .build())
                .build();

        // Explicit dependency — see the comment above kbPolicy for why this is required, not
        // just defensive: without it, CloudFormation has no ordering constraint forcing the
        // permissions to exist before Bedrock validates them during KB creation.
        knowledgeBase.getNode().addDependency(kbPolicy);

        // --- Data sources ------------------------------------------------------------------
        // Two data sources over two disjoint staging prefixes in the SAME uploads bucket —
        // never two data sources scanning users/, and staging content never re-enters the
        // pipeline because the S3 event notification below is filtered to users/ only.
        CfnDataSource multimodalDataSource = CfnDataSource.Builder.create(this, "MultimodalDataSource")
                .knowledgeBaseId(knowledgeBase.getAttrKnowledgeBaseId())
                .name("memory-layer-multimodal")
                .dataSourceConfiguration(CfnDataSource.DataSourceConfigurationProperty.builder()
                        .type("S3")
                        .s3Configuration(CfnDataSource.S3DataSourceConfigurationProperty.builder()
                                .bucketArn(uploadsBucket.getBucketArn())
                                .inclusionPrefixes(List.of("kb/multimodal/"))
                                .build())
                        .build())
                .vectorIngestionConfiguration(CfnDataSource.VectorIngestionConfigurationProperty.builder()
                        .parsingConfiguration(CfnDataSource.ParsingConfigurationProperty.builder()
                                .parsingStrategy("BEDROCK_DATA_AUTOMATION")
                                // Deployment-time bug found and fixed: without this, BDA
                                // rejected every real JPEG/PNG/WAV/MP4 test file as
                                // "file format was not supported" and only PDF got through —
                                // BDA defaults to a text/document-only parsing mode unless
                                // MULTIMODAL is explicitly requested. This is the SDK's only
                                // defined ParsingModality value (confirmed via the model enum).
                                .bedrockDataAutomationConfiguration(CfnDataSource.BedrockDataAutomationConfigurationProperty.builder()
                                        .parsingModality("MULTIMODAL")
                                        .build())
                                .build())
                        .build())
                .build();

        CfnDataSource textDataSource = CfnDataSource.Builder.create(this, "TextDataSource")
                .knowledgeBaseId(knowledgeBase.getAttrKnowledgeBaseId())
                .name("memory-layer-text")
                .dataSourceConfiguration(CfnDataSource.DataSourceConfigurationProperty.builder()
                        .type("S3")
                        .s3Configuration(CfnDataSource.S3DataSourceConfigurationProperty.builder()
                                .bucketArn(uploadsBucket.getBucketArn())
                                .inclusionPrefixes(List.of("kb/text/"))
                                .build())
                        .build())
                .build();

        // --- SQS ingestion queue -------------------------------------------------------------
        // Created in DataStack, not here — see DataStack's Javadoc for why (the bucket's own
        // event notification would otherwise create a cross-stack dependency cycle).
        Queue ingestionQueue = dataStack.getIngestionQueue();

        // --- Ingestion Coordinator Lambda ---------------------------------------------------
        LogGroup coordinatorLogGroup = LogGroup.Builder.create(this, "CoordinatorLogGroup")
                .logGroupName("/aws/lambda/memory-layer-ingestion-coordinator")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Function coordinatorFunction = Function.Builder.create(this, "CoordinatorFunction")
                .functionName("memory-layer-ingestion-coordinator")
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.ARM_64)
                .handler("com.memorylayer.api.ingestion.IngestionCoordinatorHandler::handleRequest")
                .code(Code.fromAsset("../Backend/target/backend.jar"))
                .memorySize(1024)
                .timeout(Duration.seconds(60))
                // Docs/ARCHITECTURE.md §7.3 calls for reserved concurrency 1, but this AWS
                // account's total Lambda concurrency limit is 10 (`aws lambda
                // get-account-settings`), and AWS requires >=10 UnreservedConcurrentExecutions
                // to remain at all times — so no function in this account can reserve any
                // concurrency without deployment failing (confirmed: deploy failed with
                // "decreases account's UnreservedConcurrentExecution below its minimum value
                // of [10]"). Left unreserved; this is a secondary defense-in-depth measure,
                // not the primary correctness mechanism — the Knowledge Base's own
                // single-concurrent-ingestion-job limit plus this handler's ConflictException
                // handling (see IngestionCoordinatorHandler) is what actually prevents
                // concurrent/duplicate ingestion jobs, and that holds regardless of Lambda
                // concurrency. Revisit if the account's concurrency quota is ever raised.
                .logGroup(coordinatorLogGroup)
                .environment(Map.of(
                        "TABLE_NAME", dataStack.getTable().getTableName(),
                        "UPLOADS_BUCKET_NAME", uploadsBucket.getBucketName(),
                        "KNOWLEDGE_BASE_ID", knowledgeBase.getAttrKnowledgeBaseId(),
                        "MULTIMODAL_DATA_SOURCE_ID", multimodalDataSource.getAttrDataSourceId(),
                        "TEXT_DATA_SOURCE_ID", textDataSource.getAttrDataSourceId()))
                .build();

        dataStack.getTable().grantReadWriteData(coordinatorFunction);
        uploadsBucket.grantRead(coordinatorFunction, "users/*");
        uploadsBucket.grantReadWrite(coordinatorFunction, "kb/*");
        // Deployment-time bug found and fixed: StartIngestionJob's IAM resource-level check is
        // against the bare Knowledge Base ARN, not a "/data-source/*" sub-resource ARN as
        // assumed at design time — confirmed by the actual AccessDeniedException, which named
        // exactly the KB ARN with no suffix as the resource it checked.
        coordinatorFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("bedrock:StartIngestionJob"))
                .resources(List.of(knowledgeBase.getAttrKnowledgeBaseArn()))
                .build());

        coordinatorFunction.addEventSource(new SqsEventSource(ingestionQueue, SqsEventSourceProps.builder()
                .batchSize(10)
                .maxBatchingWindow(Duration.seconds(10))
                .reportBatchItemFailures(true)
                .build()));

        // --- Status Reconciler Lambda + EventBridge schedule --------------------------------
        LogGroup reconcilerLogGroup = LogGroup.Builder.create(this, "ReconcilerLogGroup")
                .logGroupName("/aws/lambda/memory-layer-status-reconciler")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Function reconcilerFunction = Function.Builder.create(this, "ReconcilerFunction")
                .functionName("memory-layer-status-reconciler")
                .runtime(Runtime.JAVA_21)
                .architecture(Architecture.ARM_64)
                .handler("com.memorylayer.api.ingestion.StatusReconcilerHandler::handleRequest")
                .code(Code.fromAsset("../Backend/target/backend.jar"))
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .logGroup(reconcilerLogGroup)
                .environment(Map.of(
                        "TABLE_NAME", dataStack.getTable().getTableName(),
                        "KNOWLEDGE_BASE_ID", knowledgeBase.getAttrKnowledgeBaseId()))
                .build();

        dataStack.getTable().grantReadWriteData(reconcilerFunction);
        // Same fix as StartIngestionJob above — GetIngestionJob also checks against the bare
        // Knowledge Base ARN.
        reconcilerFunction.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("bedrock:GetIngestionJob"))
                .resources(List.of(knowledgeBase.getAttrKnowledgeBaseArn()))
                .build());

        Rule.Builder.create(this, "ReconcilerSchedule")
                .schedule(Schedule.rate(Duration.minutes(1)))
                .targets(List.of(new LambdaFunction(reconcilerFunction)))
                .build();

        CfnOutput.Builder.create(this, "KnowledgeBaseId").value(knowledgeBase.getAttrKnowledgeBaseId()).build();
    }
}
