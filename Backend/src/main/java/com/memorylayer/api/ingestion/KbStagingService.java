package com.memorylayer.api.ingestion;

import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.KbParsingPath;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

/**
 * Routes an uploaded source object into its Knowledge Base staging prefix and writes the
 * metadata sidecar beside it there — the design amendment from the Phase 4 plan review:
 * each data source watches its own {@code kb/multimodal/} or {@code kb/text/} prefix rather
 * than two data sources scanning the shared {@code users/} prefix.
 *
 * <p>The original {@code users/<userId>/documents/<documentId>/original/<fileName>} object is
 * left untouched; this only ever adds a copy plus a sidecar under {@code kb/}.
 */
public class KbStagingService {

    private final S3Client s3Client;
    private final String bucketName;

    public KbStagingService(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /** Copies the source object to {@code kb/<path>/<documentId>/<fileName>} and writes its
     * sidecar, then returns the staging key. Document-scoped (not just filename-scoped) so
     * two different users' same-named files can't collide in the shared staging prefix. */
    public String stage(Document document, KbParsingPath path) {
        String stagingKey = path.stagingPrefix() + document.getDocumentId() + "/" + document.getFileName();

        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(document.getS3Key())
                .destinationBucket(bucketName)
                .destinationKey(stagingKey)
                .build());

        String sidecarJson = KbMetadataSidecar.build(document);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(stagingKey + ".metadata.json")
                        .contentType("application/json")
                        .build(),
                RequestBody.fromString(sidecarJson, StandardCharsets.UTF_8));

        return stagingKey;
    }
}
