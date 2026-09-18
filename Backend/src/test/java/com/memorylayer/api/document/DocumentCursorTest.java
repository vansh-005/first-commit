package com.memorylayer.api.document;

import com.memorylayer.api.error.InvalidRequestException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentCursorTest {

    @Test
    void encodesAndDecodesRoundTrip() {
        Map<String, AttributeValue> lastEvaluatedKey = Map.of(
                "PK", AttributeValue.builder().s("USER#abc123").build(),
                "SK", AttributeValue.builder().s("DOC#doc-1").build(),
                "GSI1PK", AttributeValue.builder().s("USER#abc123").build(),
                "GSI1SK", AttributeValue.builder().s("2026-09-18T03:00:00Z#doc-1").build());

        String cursor = DocumentCursor.encode(lastEvaluatedKey);
        Map<String, AttributeValue> decoded = DocumentCursor.decode(cursor);

        assertEquals(lastEvaluatedKey, decoded);
    }

    @Test
    void emptyLastEvaluatedKeyEncodesToNull() {
        assertNull(DocumentCursor.encode(Map.of()));
        assertNull(DocumentCursor.encode(null));
    }

    @Test
    void blankCursorDecodesToNull() {
        assertNull(DocumentCursor.decode(null));
        assertNull(DocumentCursor.decode(""));
    }

    @Test
    void garbageCursorThrowsInvalidRequest() {
        assertThrows(InvalidRequestException.class, () -> DocumentCursor.decode("not-a-real-cursor!!"));
    }
}
