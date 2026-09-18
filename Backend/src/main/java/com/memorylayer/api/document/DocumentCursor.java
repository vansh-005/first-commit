package com.memorylayer.api.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylayer.api.error.InvalidRequestException;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps DynamoDB's LastEvaluatedKey as an opaque cursor string per Docs/API.md §15 — the
 * frontend must not depend on its internal representation. All of GSI1PK/GSI1SK/PK/SK are
 * plain strings, so no type tagging is needed.
 */
public final class DocumentCursor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocumentCursor() {
    }

    public static String encode(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        Map<String, String> plain = new LinkedHashMap<>();
        lastEvaluatedKey.forEach((key, value) -> plain.put(key, value.s()));
        try {
            byte[] json = MAPPER.writeValueAsBytes(plain);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode pagination cursor", e);
        }
    }

    public static Map<String, AttributeValue> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(cursor);
            Map<String, String> plain = MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
            });
            Map<String, AttributeValue> key = new LinkedHashMap<>();
            plain.forEach((k, v) -> key.put(k, AttributeValue.builder().s(v).build()));
            return key;
        } catch (Exception e) {
            throw new InvalidRequestException("Invalid pagination cursor");
        }
    }
}
