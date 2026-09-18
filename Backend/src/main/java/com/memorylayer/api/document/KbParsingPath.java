package com.memorylayer.api.document;

import java.util.Locale;
import java.util.Set;

/**
 * Which Knowledge Base data source/parser a file's staged copy is routed to.
 * Docs/API.md §13 — the two lists below are exhaustive and copied from that doc.
 */
public enum KbParsingPath {
    /** Bedrock Data Automation parser, staged under {@code kb/multimodal/}. */
    MULTIMODAL,
    /** Default text-document parser, staged under {@code kb/text/}. */
    TEXT;

    private static final Set<String> MULTIMODAL_EXTENSIONS = Set.of(
            "pdf", "jpeg", "jpg", "png",
            "mp3", "wav", "m4a", "flac", "ogg", "amr",
            "mp4", "mov");

    /** Routes by the file's extension, per the exact lists in Docs/API.md §13. Falls back to
     * {@link #TEXT} for anything unrecognized, since the default parser accepts arbitrary
     * text-like content without failing the way BDA would on an unsupported format. */
    public static KbParsingPath fromFileName(String fileName) {
        String extension = extensionOf(fileName);
        if (MULTIMODAL_EXTENSIONS.contains(extension)) {
            return MULTIMODAL;
        }
        return TEXT;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** S3 staging prefix this path's data source is scoped to (no leading/trailing content
     * beyond the fixed segment — callers append {@code documentId/fileName}). */
    public String stagingPrefix() {
        return this == MULTIMODAL ? "kb/multimodal/" : "kb/text/";
    }
}
