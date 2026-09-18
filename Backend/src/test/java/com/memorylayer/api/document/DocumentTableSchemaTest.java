package com.memorylayer.api.document;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TableSchema.fromBean() derives each DynamoDB attribute name from the Java property name
 * (e.g. getPk() -> "pk") unless overridden with @DynamoDbAttribute — which silently produces
 * an item missing the table's actual (uppercase) key attributes, only failing at real
 * DynamoDB call time with "Missing the key PK in the item". This test catches that class of
 * mistake without needing a real table.
 */
class DocumentTableSchemaTest {

    @Test
    void beanMapsToTheActualUppercaseTableKeyAttributeNames() {
        TableSchema<Document> schema = TableSchema.fromBean(Document.class);

        Document document = new Document();
        document.setPk("USER#abc123");
        document.setSk("DOC#doc-1");
        document.setGsi1Pk("USER#abc123");
        document.setGsi1Sk("2026-09-18T03:00:00Z#doc-1");

        Map<String, AttributeValue> item = schema.itemToMap(document, true);

        assertTrue(item.containsKey("PK"), "expected literal 'PK' attribute, got keys: " + item.keySet());
        assertTrue(item.containsKey("SK"), "expected literal 'SK' attribute, got keys: " + item.keySet());
        assertTrue(item.containsKey("GSI1PK"), "expected literal 'GSI1PK' attribute, got keys: " + item.keySet());
        assertTrue(item.containsKey("GSI1SK"), "expected literal 'GSI1SK' attribute, got keys: " + item.keySet());

        assertEquals("USER#abc123", item.get("PK").s());
        assertEquals("DOC#doc-1", item.get("SK").s());
    }
}
