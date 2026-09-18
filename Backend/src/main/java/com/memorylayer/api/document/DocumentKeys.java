package com.memorylayer.api.document;

/** Key-building conventions from Docs/DATA_MODEL.md §5 and §16 (AP1/AP2), centralized so
 * every read/write path constructs them identically. */
public final class DocumentKeys {

    private DocumentKeys() {
    }

    public static String userPartitionKey(String userId) {
        return "USER#" + userId;
    }

    public static String documentSortKey(String documentId) {
        return "DOC#" + documentId;
    }

    public static String gsi1SortKey(String createdAt, String documentId) {
        return createdAt + "#" + documentId;
    }
}
