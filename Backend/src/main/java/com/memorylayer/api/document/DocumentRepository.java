package com.memorylayer.api.document;

import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;

/** Data-access layer over the single-table DynamoDB design in Docs/DATA_MODEL.md §3–5. */
@Repository
public class DocumentRepository {

    private final DynamoDbTable<Document> table;
    private final DynamoDbIndex<Document> gsi1;

    public DocumentRepository(DynamoDbEnhancedClient enhancedClient) {
        String tableName = System.getenv("TABLE_NAME");
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Document.class));
        this.gsi1 = table.index("GSI1");
    }

    public void save(Document document) {
        table.putItem(document);
    }

    /** AP1 (Docs/DATA_MODEL.md §16): direct lookup scoped to the owning user's partition —
     * a document belonging to a different user simply doesn't exist from this query's
     * perspective, which is the structural ownership boundary. */
    public Optional<Document> findByUserAndDocumentId(String userId, String documentId) {
        Document found = table.getItem(Key.builder()
                .partitionValue(DocumentKeys.userPartitionKey(userId))
                .sortValue(DocumentKeys.documentSortKey(documentId))
                .build());
        return Optional.ofNullable(found);
    }

    public record DocumentPage(List<Document> items, String nextCursor) {
    }

    /** AP2 (Docs/DATA_MODEL.md §16): chronological (newest-first) listing via GSI1. */
    public DocumentPage queryByUserChronological(String userId, int limit, String cursor) {
        QueryConditional condition = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(DocumentKeys.userPartitionKey(userId))
                .build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .scanIndexForward(false)
                .limit(limit);

        var exclusiveStartKey = DocumentCursor.decode(cursor);
        if (exclusiveStartKey != null) {
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Page<Document> page = gsi1.query(requestBuilder.build()).iterator().next();
        return new DocumentPage(page.items(), DocumentCursor.encode(page.lastEvaluatedKey()));
    }
}
