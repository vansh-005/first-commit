package com.memorylayer.infra;

import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.List;
import java.util.Map;

class DataStackTest {

    @Test
    void createsPayPerRequestTableWithGsi1AndPrivateEncryptedBucketWithCors() {
        App app = new App();
        Environment env = Environment.builder().account("123456789012").region("ap-south-1").build();
        DataStack stack = new DataStack(app, "TestDataStack", StackProps.builder().env(env).build(),
                "https://main.example.amplifyapp.com");
        Template template = Template.fromStack(stack);

        template.hasResourceProperties("AWS::DynamoDB::Table", Match.objectLike(Map.of(
                "BillingMode", "PAY_PER_REQUEST",
                "KeySchema", List.of(
                        Map.of("AttributeName", "PK", "KeyType", "HASH"),
                        Map.of("AttributeName", "SK", "KeyType", "RANGE")),
                "GlobalSecondaryIndexes", Match.arrayWith(List.of(Match.objectLike(Map.of(
                        "IndexName", "GSI1",
                        "KeySchema", List.of(
                                Map.of("AttributeName", "GSI1PK", "KeyType", "HASH"),
                                Map.of("AttributeName", "GSI1SK", "KeyType", "RANGE"))
                ))))
        )));

        template.hasResourceProperties("AWS::S3::Bucket", Match.objectLike(Map.of(
                "PublicAccessBlockConfiguration", Match.objectLike(Map.of(
                        "BlockPublicAcls", true,
                        "BlockPublicPolicy", true,
                        "IgnorePublicAcls", true,
                        "RestrictPublicBuckets", true
                )),
                "BucketEncryption", Match.objectLike(Map.of(
                        "ServerSideEncryptionConfiguration", Match.arrayWith(List.of(Match.objectLike(Map.of(
                                "ServerSideEncryptionByDefault", Map.of("SSEAlgorithm", "AES256")
                        ))))
                )),
                "CorsConfiguration", Match.objectLike(Map.of(
                        "CorsRules", Match.arrayWith(List.of(Match.objectLike(Map.of(
                                "AllowedMethods", Match.arrayWith(List.of("PUT", "GET", "HEAD")),
                                "AllowedOrigins", List.of(
                                        "https://main.example.amplifyapp.com",
                                        "http://localhost:5173")
                        ))))
                ))
        )));
    }
}
