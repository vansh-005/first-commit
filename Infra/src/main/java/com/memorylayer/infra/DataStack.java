package com.memorylayer.infra;

import software.amazon.awscdk.CfnOutput;
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
import software.constructs.Construct;

import java.util.List;

/**
 * Phase 3: storage only — the single-table DynamoDB design and the private uploads bucket
 * from Docs/DATA_MODEL.md §3 and §8. No Bedrock Knowledge Base metadata sidecars are
 * written here; that is Phase 4's ingestion coordinator's responsibility, once the S3
 * object actually exists, per project decision (avoids orphaned metadata files).
 */
public class DataStack extends Stack {

    private final Table table;
    private final Bucket uploadsBucket;

    public DataStack(final Construct scope, final String id, final StackProps props, final String amplifyOrigin) {
        super(scope, id, props);

        this.table = Table.Builder.create(this, "MemoryLayerTable")
                .tableName("MemoryLayer")
                .partitionKey(Attribute.builder().name("PK").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("SK").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
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

        CfnOutput.Builder.create(this, "TableName").value(table.getTableName()).build();
        CfnOutput.Builder.create(this, "UploadsBucketName").value(uploadsBucket.getBucketName()).build();
    }

    public Table getTable() {
        return table;
    }

    public Bucket getUploadsBucket() {
        return uploadsBucket;
    }
}
