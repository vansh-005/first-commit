package com.memorylayer.api.document;

/** Normalized file category, stored once so the UI never has to infer it. Docs/DATA_MODEL.md §7. */
public enum MediaCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER;

    private static final java.util.Set<String> DOCUMENT_MIME_TYPES = java.util.Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public static MediaCategory fromMimeType(String mimeType) {
        if (mimeType == null) {
            return OTHER;
        }
        String type = mimeType.toLowerCase(java.util.Locale.ROOT);
        if (type.startsWith("image/")) {
            return IMAGE;
        }
        if (type.startsWith("video/")) {
            return VIDEO;
        }
        if (type.startsWith("audio/")) {
            return AUDIO;
        }
        if (type.startsWith("text/") || DOCUMENT_MIME_TYPES.contains(type)) {
            return DOCUMENT;
        }
        return OTHER;
    }
}
