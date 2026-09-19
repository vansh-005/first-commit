package com.memorylayer.api.observability;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 7. One JSON line per structured event, written to stdout so CloudWatch Logs captures
 * it verbatim (both Lambda's own runtime logging and Spring Boot's console appender already
 * ship stdout to CloudWatch — no new log destination is introduced). Deliberately independent
 * of any logging framework so the same class works from Spring-managed API code and the plain
 * {@code RequestHandler} ingestion Lambdas (Coordinator/Reconciler/stale-cleanup), none of
 * which share a DI container.
 *
 * <p>This does not replace existing human-readable log lines (slf4j in the API,
 * {@code context.getLogger().log(...)} in the plain Lambdas) — it adds a second, queryable,
 * alarm-friendly line at the same key events, per Docs/API.md §27's structured-logging
 * contract (requestId/documentId/userId/latencyMs/downstream fields) plus this phase's
 * addition of jobId and a safe error type.
 *
 * <p>Never log: JWTs, presigned URLs, Ask/Search question or answer content, raw file
 * content, or raw Bedrock session IDs. {@link #hashUserId(String)} is provided so a userId is
 * always logged as a one-way hash, never the raw Cognito {@code sub}, per Docs/API.md §27's
 * "userIdHash or safe internal user identifier" guidance.
 */
public final class StructuredLog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredLog() {
    }

    public static void info(String event, Map<String, Object> fields) {
        emit("INFO", event, fields);
    }

    public static void warn(String event, Map<String, Object> fields) {
        emit("WARN", event, fields);
    }

    public static void error(String event, Map<String, Object> fields) {
        emit("ERROR", event, fields);
    }

    private static void emit(String level, String event, Map<String, Object> fields) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("level", level);
        record.put("event", event);
        record.put("timestamp", Instant.now().toString());
        if (fields != null) {
            record.putAll(fields);
        }
        try {
            System.out.println(MAPPER.writeValueAsString(record));
        } catch (Exception e) {
            // A logging failure must never break the caller's actual work.
            System.out.println("{\"level\":\"ERROR\",\"event\":\"structured_log_emit_failed\"}");
        }
    }

    /** SHA-256 of the userId, hex-encoded and truncated to 16 characters — enough entropy to
     * correlate log lines for the same user across a request/investigation without logging the
     * raw Cognito {@code sub}. Not cryptographically sensitive data; this is a correlation
     * token, not a security boundary. */
    public static String hashUserId(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unhashable";
        }
    }
}
