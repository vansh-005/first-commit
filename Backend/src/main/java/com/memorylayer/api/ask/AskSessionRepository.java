package com.memorylayer.api.ask;

import com.memorylayer.api.document.DocumentKeys;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/** Data-access layer for {@link AskSession}, Docs/DATA_MODEL.md §15a. Shares the same table
 * as {@code Document} (single-table design, Docs/DATA_MODEL.md §3) but its own sort-key
 * prefix, so the two entity types coexist under one user partition without colliding. */
@Repository
public class AskSessionRepository {

    private static final String SORT_KEY_PREFIX = "ASK_SESSION#";

    private final DynamoDbTable<AskSession> table;

    public AskSessionRepository(DynamoDbEnhancedClient enhancedClient) {
        String tableName = System.getenv("TABLE_NAME");
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(AskSession.class));
    }

    public void save(AskSession session) {
        table.putItem(session);
    }

    /** Resolution is always scoped to {@code USER#<userId>} — an application sessionId that
     * belongs to a different user (or never existed, or already expired/was deleted) simply
     * doesn't resolve, which is indistinguishable by design (Phase 6 amendment: "resolve it
     * under the authenticated user's partition before calling Bedrock"). */
    public Optional<AskSession> findByUserAndSessionId(String userId, String applicationSessionId) {
        AskSession found = table.getItem(Key.builder()
                .partitionValue(DocumentKeys.userPartitionKey(userId))
                .sortValue(sortKey(applicationSessionId))
                .build());
        return Optional.ofNullable(found);
    }

    /** Called when Bedrock rejects the underlying session as invalid/expired — the mapping
     * must not linger and be reused again, per the Phase 6 amendment against transparently
     * retrying a contextual follow-up with no history. */
    public void delete(String userId, String applicationSessionId) {
        table.deleteItem(Key.builder()
                .partitionValue(DocumentKeys.userPartitionKey(userId))
                .sortValue(sortKey(applicationSessionId))
                .build());
    }

    public static String sortKey(String applicationSessionId) {
        return SORT_KEY_PREFIX + applicationSessionId;
    }
}
