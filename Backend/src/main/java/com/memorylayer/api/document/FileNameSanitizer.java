package com.memorylayer.api.document;

import java.util.regex.Pattern;

/** Strips path separators and unsafe characters before a client-supplied filename becomes
 * part of an S3 object key. Docs/API.md §10: "the backend sanitizes it before including it
 * in an S3 object key." */
public final class FileNameSanitizer {

    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^A-Za-z0-9._-]");

    private FileNameSanitizer() {
    }

    public static String sanitize(String fileName) {
        String base = fileName.replace('\\', '/');
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash >= 0) {
            base = base.substring(lastSlash + 1);
        }
        String sanitized = UNSAFE_CHARS.matcher(base).replaceAll("_");
        return sanitized.isBlank() ? "file" : sanitized;
    }
}
