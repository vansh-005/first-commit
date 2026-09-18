package com.memorylayer.api.aws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * SDK clients as singleton beans so SnapStart's snapshot captures them already initialized,
 * rather than paying client-construction cost on every cold restore.
 *
 * <p>Region is set explicitly (from the {@code AWS_REGION} env var Lambda always provides,
 * falling back to {@code ap-south-1} otherwise) rather than left to the default region
 * provider chain, so these beans construct deterministically in any environment — including
 * local test runs with no ambient AWS CLI configuration — without needing real credentials
 * at construction time.
 */
@Configuration
public class AwsClientsConfig {

    private static Region region() {
        String region = System.getenv("AWS_REGION");
        return Region.of(region != null && !region.isBlank() ? region : "ap-south-1");
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(region())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder().region(region()).build();
    }

    /** Phase 5: POST /api/v1/search's Retrieve calls. */
    @Bean
    public BedrockAgentRuntimeClient bedrockAgentRuntimeClient() {
        return BedrockAgentRuntimeClient.builder()
                .region(region())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
