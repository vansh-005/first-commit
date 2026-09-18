package com.memorylayer.api.ingestion;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.GetIngestionJobRequest;
import software.amazon.awssdk.services.bedrockagent.model.GetIngestionJobResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * EventBridge-scheduled poller (~once/minute) that reconciles in-flight ingestion jobs against
 * Bedrock, per Docs/ARCHITECTURE.md §7.5. Never runs a Lambda that blocks waiting on ingestion
 * — this checks once, updates whatever finished, and returns.
 */
public class StatusReconcilerHandler implements RequestHandler<ScheduledEvent, Void> {

    /** Docs/DATA_MODEL.md §14: keep completed job records for 7 days, then let TTL clean
     * them up — they're operational state, not permanent user data. */
    private static final Duration JOB_RECORD_RETENTION = Duration.ofDays(7);

    // API.md §6/§27: never surface raw Bedrock failure internals to the document record —
    // the real reason is logged to CloudWatch instead (see reconcileFailed).
    private static final String SAFE_FAILURE_REASON = "Processing failed. Please try re-uploading the file.";

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final BedrockAgentClient bedrockAgentClient;
    private final String knowledgeBaseId;

    public StatusReconcilerHandler() {
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
        this.bedrockAgentClient = BedrockAgentClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.knowledgeBaseId = System.getenv("KNOWLEDGE_BASE_ID");
    }

    private static Region resolveRegion() {
        String region = System.getenv("AWS_REGION");
        return Region.of(region != null && !region.isBlank() ? region : "ap-south-1");
    }

    @Override
    public Void handleRequest(ScheduledEvent event, Context context) {
        for (IngestionJob job : ingestionJobRepository.findAll()) {
            if (job.getStatus() != IngestionJobStatus.STARTING && job.getStatus() != IngestionJobStatus.IN_PROGRESS) {
                continue;
            }

            GetIngestionJobResponse response = bedrockAgentClient.getIngestionJob(GetIngestionJobRequest.builder()
                    .knowledgeBaseId(knowledgeBaseId)
                    .dataSourceId(job.getDataSourceId())
                    .ingestionJobId(job.getJobId())
                    .build());

            var bedrockStatus = response.ingestionJob().status();
            switch (bedrockStatus) {
                case COMPLETE -> reconcileComplete(job, response, context);
                case FAILED, STOPPED -> reconcileFailed(job, response, context);
                default -> {
                    // STARTING / IN_PROGRESS / STOPPING: still running, nothing to do yet.
                }
            }
        }
        return null;
    }

    /** A {@code COMPLETE} job status does not mean every document in it was indexed
     * successfully — Bedrock reports per-file failures (e.g. an unsupported format) via
     * {@code statistics().numberOfDocumentsFailed()} while still marking the overall job
     * COMPLETE. This was observed directly in the Phase 4 spike: a job covering one
     * unsupported PNG completed with {@code numberOfDocumentsFailed=1}, not a FAILED status.
     * Bedrock's API does not identify *which* document(s) failed, so — per explicit
     * instruction — this only marks the job's documents READY when the failed count is
     * exactly zero; any nonzero count routes to the same conservative FAILED handling as a
     * genuinely failed job, rather than guessing which of the covered documents actually
     * succeeded. */
    private void reconcileComplete(IngestionJob job, GetIngestionJobResponse response, Context context) {
        long failedCount = countFailedDocuments(response);
        if (failedCount > 0) {
            context.getLogger().log("Ingestion job " + job.getJobId() + " completed with "
                    + failedCount + " failed document(s) out of its batch; marking all "
                    + job.getDocumentIds().size() + " covered document(s) FAILED conservatively "
                    + "(per-document failure identification is not implemented).");
            reconcileFailed(job, response, context);
            return;
        }

        String now = Instant.now().toString();
        for (String documentRef : job.getDocumentIds()) {
            findDocument(documentRef).ifPresent(document -> {
                if (document.getStatus() == DocumentStatus.INDEXING) {
                    document.setStatus(DocumentStatus.READY);
                    document.setUpdatedAt(now);
                    documentRepository.save(document);
                }
            });
        }

        job.setStatus(IngestionJobStatus.COMPLETE);
        job.setCompletedAt(now);
        job.setUpdatedAt(now);
        job.setExpiresAt(Instant.now().plus(JOB_RECORD_RETENTION).getEpochSecond());
        ingestionJobRepository.save(job);
    }

    private static long countFailedDocuments(GetIngestionJobResponse response) {
        var statistics = response.ingestionJob().statistics();
        if (statistics == null || statistics.numberOfDocumentsFailed() == null) {
            return 0L;
        }
        return statistics.numberOfDocumentsFailed();
    }

    private void reconcileFailed(IngestionJob job, GetIngestionJobResponse response, Context context) {
        List<String> failureReasons = response.ingestionJob().failureReasons();
        context.getLogger().log("Ingestion job " + job.getJobId() + " failed: " + failureReasons);

        String now = Instant.now().toString();
        for (String documentRef : job.getDocumentIds()) {
            findDocument(documentRef).ifPresent(document -> {
                if (document.getStatus() == DocumentStatus.INDEXING) {
                    document.setStatus(DocumentStatus.FAILED);
                    document.setFailureReason(SAFE_FAILURE_REASON);
                    document.setUpdatedAt(now);
                    documentRepository.save(document);
                }
            });
        }

        job.setStatus(IngestionJobStatus.FAILED);
        job.setFailureReason(SAFE_FAILURE_REASON);
        job.setCompletedAt(now);
        job.setUpdatedAt(now);
        job.setExpiresAt(Instant.now().plus(JOB_RECORD_RETENTION).getEpochSecond());
        ingestionJobRepository.save(job);
    }

    private Optional<Document> findDocument(String documentRef) {
        int separator = documentRef.indexOf('#');
        if (separator < 0) {
            return Optional.empty();
        }
        String userId = documentRef.substring(0, separator);
        String documentId = documentRef.substring(separator + 1);
        return documentRepository.findByUserAndDocumentId(userId, documentId);
    }
}
