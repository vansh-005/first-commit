package com.memorylayer.api.search;

import com.memorylayer.api.document.DocumentKeys;
import com.memorylayer.api.dto.MediaTimestamp;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievedReference;

import java.util.Locale;
import java.util.Map;

/**
 * Pure, Spring-free chunk-mapping logic shared by {@code Retrieve} ({@link SearchResultMapper})
 * and {@code RetrieveAndGenerate} (Phase 6's Ask citation mapping): documentId extraction,
 * snippet resolution, and media-timestamp resolution.
 *
 * <p>{@link KnowledgeBaseRetrievalResult} (Search's per-chunk result) and
 * {@link RetrievedReference} (Ask's per-citation grounding reference) both expose the same
 * {@code content()}/{@code location()}/{@code metadata()} shape — confirmed via {@code javap}
 * during Phase 6 planning — so this class operates on those three values directly via
 * {@link ChunkRef} rather than duplicating the logic per SDK type.
 *
 * <p>Snippet resolution deliberately does not assume undocumented modality-specific SDK
 * fields. It parses the actual response shape confirmed against our deployed BDA Knowledge
 * Base during Phase 4/5 verification: {@code content.text} for text chunks,
 * {@code content.audio().transcription()} / {@code content.video().summary()} for audio/video
 * (real typed fields, confirmed present only after bumping the AWS SDK — see Backend/pom.xml),
 * and the {@code x-amz-bedrock-kb-description} metadata attribute for images (verified live;
 * images have no dedicated typed content field, only {@code byteContent}, which isn't usable
 * as a text snippet). Anything else falls back to a safe, non-technical message.
 */
public final class RetrievalContentMapper {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RetrievalContentMapper.class);

    private static final int SNIPPET_MAX_LENGTH = 300;

    private RetrievalContentMapper() {
    }

    /** The three fields every chunk-like Bedrock shape exposes, extracted once so the mapping
     * logic below doesn't care which SDK response type it came from. */
    public record ChunkRef(RetrievalResultContent content, RetrievalResultLocation location, Map<String, Document> metadata) {

        public static ChunkRef of(KnowledgeBaseRetrievalResult result) {
            return new ChunkRef(result.content(), result.location(), result.metadata());
        }

        public static ChunkRef of(RetrievedReference reference) {
            return new ChunkRef(reference.content(), reference.location(), reference.metadata());
        }
    }

    /** Primary path: the {@code documentId} metadata attribute the ingestion coordinator
     * always writes into the Knowledge Base sidecar. Fallback: parse it back out of the
     * staging S3 URI, in case metadata is ever unexpectedly absent on a result. */
    public static String extractDocumentId(ChunkRef ref) {
        String fromMetadata = asString(ref.metadata().get("documentId"));
        if (fromMetadata != null && !fromMetadata.isBlank()) {
            return fromMetadata;
        }
        if (ref.location() != null && ref.location().s3Location() != null) {
            String uri = ref.location().s3Location().uri();
            if (uri != null) {
                return DocumentKeys.parseStagingDocumentId(stripS3Scheme(uri));
            }
        }
        return null;
    }

    public static String resolveSnippet(ChunkRef ref) {
        RetrievalResultContent content = ref.content();
        if (content != null) {
            String text = content.text();
            if (text != null && !text.isBlank()) {
                return truncate(text);
            }
            if (content.audio() != null) {
                String transcription = content.audio().transcription();
                if (transcription != null && !transcription.isBlank()) {
                    return truncate(transcription);
                }
            }
            if (content.video() != null) {
                String summary = content.video().summary();
                if (summary != null && !summary.isBlank()) {
                    return truncate(summary);
                }
            }
        }

        String description = asString(ref.metadata().get("x-amz-bedrock-kb-description"));
        if (description != null && !description.isBlank()) {
            return truncate(description);
        }

        return safeFallback(ref.metadata());
    }

    private static String safeFallback(Map<String, Document> metadata) {
        String mediaCategory = asString(metadata.get("mediaCategory"));
        if (mediaCategory != null && !mediaCategory.isBlank()) {
            return "No preview available for this " + mediaCategory.toLowerCase(Locale.ROOT) + " file.";
        }
        return "No preview available for this file.";
    }

    /** Supports both observed forms of chunk timing metadata defensively: the confirmed-live
     * {@code x-amz-bedrock-kb-chunk-*-time-in-millis} keys, and {@code _media_*_time_ms} as an
     * alternate form. Returns null unless both a start and an end are found. */
    public static MediaTimestamp resolveMediaTimestamp(Map<String, Document> metadata) {
        if (metadata == null) {
            return null;
        }
        Long startMs = asMillis(metadata.get("x-amz-bedrock-kb-chunk-start-time-in-millis"));
        if (startMs == null) {
            startMs = asMillis(metadata.get("_media_start_time_ms"));
        }
        Long endMs = asMillis(metadata.get("x-amz-bedrock-kb-chunk-end-time-in-millis"));
        if (endMs == null) {
            endMs = asMillis(metadata.get("_media_end_time_ms"));
        }
        if (startMs == null || endMs == null) {
            return null;
        }
        return new MediaTimestamp(startMs, endMs);
    }

    /** Uses {@code Document}'s typed accessors rather than {@code unwrap()} +
     * {@code instanceof}: verified live that {@code NumberDocument.unwrap()} returns the
     * number's {@code String} form (via {@code SdkNumber.stringValue()}), never a
     * {@code java.lang.Number} — so a naive {@code instanceof Number} check never matches, and
     * {@code Long.parseLong} on a decimal string like {@code "6360.0"} throws and was being
     * swallowed. {@code SdkNumber.longValue()} correctly parses decimal-formatted strings via
     * {@code BigDecimal} internally. */
    private static Long asMillis(Document value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asNumber().longValue();
        }
        if (value.isString()) {
            try {
                return (long) Double.parseDouble(value.asString().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        log.warn("Unrecognized timestamp metadata value type: {}", value.getClass());
        return null;
    }

    private static String asString(Document value) {
        if (value == null) {
            return null;
        }
        Object raw = value.unwrap();
        return raw == null ? null : String.valueOf(raw);
    }

    private static String truncate(String text) {
        String trimmed = text.strip();
        if (trimmed.length() <= SNIPPET_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_MAX_LENGTH).stripTrailing() + "…";
    }

    /** {@code s3://bucket/kb/multimodal/<documentId>/<fileName>} -> {@code kb/multimodal/<documentId>/<fileName>}. */
    private static String stripS3Scheme(String uri) {
        String withoutScheme = uri.startsWith("s3://") ? uri.substring("s3://".length()) : uri;
        int firstSlash = withoutScheme.indexOf('/');
        return firstSlash < 0 ? withoutScheme : withoutScheme.substring(firstSlash + 1);
    }
}
