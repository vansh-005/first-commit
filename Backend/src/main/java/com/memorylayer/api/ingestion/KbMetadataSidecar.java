package com.memorylayer.api.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.memorylayer.api.document.Document;

/** Builds the Bedrock Knowledge Base {@code .metadata.json} sidecar content, per
 * Docs/DATA_MODEL.md §9. Written beside the staged copy in {@code kb/multimodal/} or
 * {@code kb/text/} — never beside the original {@code users/.../original/} object, since the
 * staging prefixes are what each data source actually watches. */
final class KbMetadataSidecar {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private KbMetadataSidecar() {
    }

    static String build(Document document) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode attributes = root.putObject("metadataAttributes");

        putStringAttribute(attributes, "userId", document.getUserId(), false);
        putStringAttribute(attributes, "documentId", document.getDocumentId(), false);
        putStringAttribute(attributes, "mediaCategory", document.getMediaCategory().name(), false);
        // Unlike the tenant/lookup attributes above, the filename is useful semantically —
        // users often remember part of a filename — so it's included in the embedding.
        putStringAttribute(attributes, "fileName", document.getFileName(), true);

        return root.toString();
    }

    private static void putStringAttribute(ObjectNode attributes, String key, String value, boolean includeForEmbedding) {
        ObjectNode attribute = attributes.putObject(key);
        ObjectNode valueNode = attribute.putObject("value");
        valueNode.put("type", "STRING");
        valueNode.put("stringValue", value);
        attribute.put("includeForEmbedding", includeForEmbedding);
    }
}
