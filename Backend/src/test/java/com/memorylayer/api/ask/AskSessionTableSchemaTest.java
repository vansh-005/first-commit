package com.memorylayer.api.ask;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks in the real DynamoDB attribute names for AskSession (the same class of mistake as Phase 3's
 * PK/SK bug: unit tests on the bean alone can't catch a mis-mapped attribute). */
class AskSessionTableSchemaTest {

    @Test
    void mapsKeysAndTheServerOwnedContextListAndAllowsANullBedrockSessionId() {
        AskSession session = new AskSession();
        session.setPk("USER#u1");
        session.setSk("ASK_SESSION#s1");
        session.setApplicationSessionId("s1");
        session.setUserId("u1");
        session.setBedrockSessionId(null);
        session.setContextDocumentIds(List.of("doc-a", "doc-b"));
        session.setExpiresAt(123L);

        Map<String, AttributeValue> item = TableSchema.fromBean(AskSession.class).itemToMap(session, true);

        assertThat(item.get("PK").s()).isEqualTo("USER#u1");
        assertThat(item.get("SK").s()).isEqualTo("ASK_SESSION#s1");
        assertThat(item.get("contextDocumentIds").l()).extracting(AttributeValue::s).containsExactly("doc-a", "doc-b");
        assertThat(item).doesNotContainKey("bedrockSessionId");
        assertThat(item.get("expiresAt").n()).isEqualTo("123");
    }

    @Test
    void roundTripsThroughTheSchema() {
        AskSession original = new AskSession();
        original.setPk("USER#u1");
        original.setSk("ASK_SESSION#s1");
        original.setContextDocumentIds(List.of("doc-a"));
        var schema = TableSchema.fromBean(AskSession.class);

        AskSession restored = schema.mapToItem(schema.itemToMap(original, true));

        assertThat(restored.getContextDocumentIds()).containsExactly("doc-a");
        assertThat(restored.getBedrockSessionId()).isNull();
    }
}
