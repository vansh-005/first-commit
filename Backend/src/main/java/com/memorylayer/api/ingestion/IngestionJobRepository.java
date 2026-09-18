package com.memorylayer.api.ingestion;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

/** Data-access layer for {@link IngestionJob}, Docs/DATA_MODEL.md §14. Deliberately not a
 * Spring {@code @Repository} — the ingestion coordinator and status reconciler are plain
 * {@code RequestHandler} Lambdas with no Spring context, per Phase 4's design (avoids paying
 * Spring Boot's cold-start cost on handlers that don't need HTTP routing). */
public class IngestionJobRepository {

    private final DynamoDbTable<IngestionJob> table;

    public IngestionJobRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(IngestionJob.class));
    }

    public void save(IngestionJob job) {
        table.putItem(job);
    }

    /** Every job record in the table — the partition is small and operational (TTL-cleaned),
     * so a single scoped query beats maintaining a GSI just to filter by status. */
    public List<IngestionJob> findAll() {
        QueryConditional condition = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(IngestionJob.PARTITION_KEY)
                .build());
        return table.query(condition).items().stream().toList();
    }

    public static String sortKey(String jobId) {
        return "JOB#" + jobId;
    }
}
