package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.s3.NotificationKeyFilter;
import software.amazon.awscdk.services.s3.notifications.SqsDestination;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.List;

/**
 * Phase 3: storage — the single-table DynamoDB design and the private uploads bucket from
 * Docs/DATA_MODEL.md §3 and §8. No Bedrock Knowledge Base metadata sidecars are written here;
 * that is Phase 4's ingestion coordinator's responsibility, once the S3 object actually
 * exists, per project decision (avoids orphaned metadata files).
 *
 * <p>Phase 4 also adds the ingestion SQS queue/DLQ and the bucket's {@code ObjectCreated}
 * notification here, even though the actual ingestion compute lives in {@code IngestionStack}.
 * A bucket's event notification is a property of the bucket resource itself, so CDK places its
 * underlying custom resource in the BUCKET's stack — wiring it in {@code IngestionStack}
 * instead (which needs the bucket ARN from this stack) would make each stack depend on the
 * other, which CDK rejects as a cyclic cross-stack reference. {@code IngestionStack} consumes
 * the queue created here instead of creating its own.
 */
public class DataStack extends Stack {

    private final Table table;
    private final Bucket uploadsBucket;
    private final Queue ingestionQueue;
    private final Queue ingestionDeadLetterQueue;

    public DataStack(final Construct scope, final String id, final StackProps props, final String amplifyOrigin) {
        super(scope, id, props);

        this.table = Table.Builder.create(this, "MemoryLayerTable")
                .tableName("MemoryLayer")
                .partitionKey(Attribute.builder().name("PK").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("SK").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                // Phase 4: SYSTEM#INGESTION job records set this once they reach a terminal
                // state (COMPLETE/FAILED) — they're operational state, not permanent user
                // data, per Docs/DATA_MODEL.md §14.
                .timeToLiveAttribute("expiresAt")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Chronological (newest-first) document listing — Docs/DATA_MODEL.md §16 AP2.
        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI1")
                .partitionKey(Attribute.builder().name("GSI1PK").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("GSI1SK").type(AttributeType.STRING).build())
                .build());

        this.uploadsBucket = Bucket.Builder.create(this, "UploadsBucket")
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .encryption(BucketEncryption.S3_MANAGED)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .cors(List.of(CorsRule.builder()
                        // PUT for direct browser uploads; GET/HEAD for presigned
                        // access/preview URLs (documents/{id}/access-url).
                        .allowedMethods(List.of(HttpMethods.PUT, HttpMethods.GET, HttpMethods.HEAD))
                        .allowedOrigins(List.of(amplifyOrigin, "http://localhost:5173"))
                        .allowedHeaders(List.of("*"))
                        .build()))
                .build();

        this.ingestionDeadLetterQueue = Queue.Builder.create(this, "IngestionDLQ")
                .retentionPeriod(Duration.days(14))
                .build();

        // Bedrock allows only ONE concurrent ingestion job per Knowledge Base — confirmed in
        // the Phase 4 spike ("You have reached the maximum number of concurrent ingestion
        // jobs per knowledge base: 1"). A burst of uploads queues up behind whichever job is
        // already running; that is expected backpressure, not a poison message, so the retry
        // budget here is sized to comfortably outlast it rather than around a fixed small
        // number of "real" retries: visibilityTimeout (5 min) x maxReceiveCount (20) gives
        // roughly 100 minutes before a healthy-but-waiting upload could reach the DLQ.
        this.ingestionQueue = Queue.Builder.create(this, "IngestionQueue")
                .visibilityTimeout(Duration.minutes(5))
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(ingestionDeadLetterQueue)
                        .maxReceiveCount(20)
                        .build())
                .build();

        // Filtered to users/ only — Knowledge Base staging objects/sidecars live under kb/
        // and must never re-enter this pipeline.
        uploadsBucket.addObjectCreatedNotification(new SqsDestination(ingestionQueue),
                NotificationKeyFilter.builder().prefix("users/").build());

        CfnOutput.Builder.create(this, "TableName").value(table.getTableName()).build();
        CfnOutput.Builder.create(this, "UploadsBucketName").value(uploadsBucket.getBucketName()).build();
        CfnOutput.Builder.create(this, "IngestionQueueUrl").value(ingestionQueue.getQueueUrl()).build();
        CfnOutput.Builder.create(this, "IngestionDLQUrl").value(ingestionDeadLetterQueue.getQueueUrl()).build();
    }

    public Table getTable() {
        return table;
    }

    public Bucket getUploadsBucket() {
        return uploadsBucket;
    }

    public Queue getIngestionQueue() {
        return ingestionQueue;
    }

    public Queue getIngestionDeadLetterQueue() {
        return ingestionDeadLetterQueue;
    }
}
