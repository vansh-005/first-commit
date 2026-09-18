package com.memorylayer.api.document;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Key-building conventions from Docs/DATA_MODEL.md §5 and §16 (AP1/AP2), centralized so
 * every read/write path constructs them identically. */
public final class DocumentKeys {

    // Mirrors the exact layout UploadController writes: users/<userId>/documents/<documentId>/original/<fileName>.
    private static final Pattern SOURCE_KEY_PATTERN =
            Pattern.compile("^users/([^/]+)/documents/([^/]+)/original/.+$");

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

    public record ParsedSourceKey(String userId, String documentId) {
    }

    /** Recovers {@code userId}/{@code documentId} from an original-upload S3 key, as seen in
     * an S3 {@code ObjectCreated} event. Returns {@code null} if the key doesn't match the
     * expected layout (defensive — the ingestion coordinator skips anything it can't parse
     * rather than guessing). */
    public static ParsedSourceKey parseSourceKey(String s3Key) {
        Matcher matcher = SOURCE_KEY_PATTERN.matcher(s3Key);
        if (!matcher.matches()) {
            return null;
        }
        return new ParsedSourceKey(matcher.group(1), matcher.group(2));
    }
}
