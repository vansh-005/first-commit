package com.memorylayer.api.ingestion;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.observability.StructuredLog;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * EventBridge-scheduled (~every 15 minutes) sweep for documents stuck outside Bedrock's own
 * tracking, per the approved Phase 7 plan. Deliberately a separate Lambda, handler class, and
 * schedule from {@link StatusReconcilerHandler} — that Lambda is Bedrock ingestion-job status's
 * authority; this one only ever looks at elapsed wall-clock time and, for
 * {@code UPLOAD_PENDING}, whether the expected S3 object exists. See {@link StaleDocumentPolicy}
 * for the exact thresholds and the reasoning behind each state's handling.
 *
 * <p>Uses a full table scan filtered by status (see
 * {@link DocumentRepository#scanByStatus(DocumentStatus)}) rather than a GSI — acceptable at
 * MVP scale for a low-frequency job; production scale would need an indexed status+time access
 * pattern instead.
 */
public class StaleDocumentCleanupHandler implements RequestHandler<ScheduledEvent, Void> {

    private static final String UPLOAD_TIMEOUT_REASON = "Upload was not completed in time. Please try uploading again.";
    private static final String PROCESSING_TIMEOUT_REASON = "Processing did not start in time. Please try again.";

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final S3Client s3Client;
    private final String uploadsBucketName;

    public StaleDocumentCleanupHandler() {
        Region region = resolveRegion();
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        String tableName = System.getenv("TABLE_NAME");

        this.documentRepository = new DocumentRepository(enhancedClient);
        this.ingestionJobRepository = new IngestionJobRepository(enhancedClient, tableName);
        this.s3Client = S3Client.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.uploadsBucketName = System.getenv("UPLOADS_BUCKET_NAME");
    }

    /** Test-only: injects mocks instead of constructing real AWS clients. */
    StaleDocumentCleanupHandler(DocumentRepository documentRepository,
                                 IngestionJobRepository ingestionJobRepository,
                                 S3Client s3Client,
                                 String uploadsBucketName) {
        this.documentRepository = documentRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.s3Client = s3Client;
        this.uploadsBucketName = uploadsBucketName;
    }

    private static Region resolveRegion() {
        String region = System.getenv("AWS_REGION");
        return Region.of(region != null && !region.isBlank() ? region : "ap-south-1");
    }

    @Override
    public Void handleRequest(ScheduledEvent event, Context context) {
        Instant now = Instant.now();
        checkUploadPending(now);
        checkUploaded(now);
        checkIndexing(now);
        return null;
    }

    private void checkUploadPending(Instant now) {
        for (Document document : documentRepository.scanByStatus(DocumentStatus.UPLOAD_PENDING)) {
            Instant createdAt = parseInstant(document.getCreatedAt());
            if (!StaleDocumentPolicy.isUploadPendingStale(createdAt, now)) {
                continue;
            }

            if (s3ObjectExists(document.getS3Key())) {
                // The object landed but never entered the pipeline (event lost, or arrived
                // before the notification was wired up) — an unexplained edge case. Leave the
                // document as-is rather than guess a resolution; flag it for a human.
                StructuredLog.warn("stale_upload_pending_object_exists", baseFields(document, now, createdAt));
                continue;
            }

            failDocument(document, now, UPLOAD_TIMEOUT_REASON, "UPLOAD_PENDING", "upload_timeout", createdAt);
        }
    }

    private void checkUploaded(Instant now) {
        Set<String> activeJobDocumentRefs = StaleDocumentPolicy.activeJobDocumentRefs(ingestionJobRepository.findAll());

        for (Document document : documentRepository.scanByStatus(DocumentStatus.UPLOADED)) {
            Instant reference = referenceTimeForUploaded(document);
            if (!StaleDocumentPolicy.isUploadedStale(reference, now)) {
                continue;
            }

            if (activeJobDocumentRefs.contains(documentRef(document))) {
                // A real, still-running Bedrock ingestion job covers this document (its own
                // status write to INDEXING failed, but StartIngestionJob already happened —
                // see IngestionCoordinatorHandler.startJobFor's Javadoc). Never auto-fail a
                // document Bedrock may still successfully index; leave it for the Status
                // Reconciler and flag it for visibility instead.
                StructuredLog.warn("stale_uploaded_active_job", baseFields(document, now, reference));
                continue;
            }

            failDocument(document, now, PROCESSING_TIMEOUT_REASON, "UPLOADED", "processing_timeout", reference);
        }
    }

    private static String documentRef(Document document) {
        return document.getUserId() + "#" + document.getDocumentId();
    }

    private void checkIndexing(Instant now) {
        for (Document document : documentRepository.scanByStatus(DocumentStatus.INDEXING)) {
            // updatedAt reflects exactly when this document entered INDEXING: nothing else
            // currently writes to a document while it is in this status without moving it out
            // of it (see IngestionCoordinatorHandler.markIndexing / StatusReconcilerHandler).
            Instant reference = parseInstant(document.getUpdatedAt());
            if (!StaleDocumentPolicy.isIndexingWarnable(reference, now)) {
                continue;
            }

            Map<String, Object> fields = baseFields(document, now, reference);
            fields.put("ingestionJobId", document.getIngestionJobId());
            StructuredLog.warn("stale_indexing_document", fields);
        }
    }

    /** Prefers {@code uploadedAt} (set the moment the coordinator confirms the S3 object
     * exists) since that is the actual start of the "waiting to be processed" window; falls
     * back to {@code updatedAt} defensively if that field is ever unexpectedly absent. */
    private static Instant referenceTimeForUploaded(Document document) {
        Instant uploadedAt = parseInstant(document.getUploadedAt());
        return uploadedAt != null ? uploadedAt : parseInstant(document.getUpdatedAt());
    }

    private void failDocument(Document document, Instant now, String reason, String fromStatus, String reasonCode, Instant reference) {
        document.setStatus(DocumentStatus.FAILED);
        document.setFailureReason(reason);
        document.setUpdatedAt(now.toString());
        documentRepository.save(document);

        Map<String, Object> fields = baseFields(document, now, reference);
        fields.put("fromStatus", fromStatus);
        fields.put("reasonCode", reasonCode);
        StructuredLog.warn("stale_document_failed", fields);
    }

    private boolean s3ObjectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(uploadsBucketName).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private static Map<String, Object> baseFields(Document document, Instant now, Instant reference) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("documentId", document.getDocumentId());
        fields.put("userIdHash", StructuredLog.hashUserId(document.getUserId()));
        fields.put("status", document.getStatus().name());
        if (reference != null) {
            fields.put("ageMinutes", java.time.Duration.between(reference, now).toMinutes());
        }
        return fields;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
