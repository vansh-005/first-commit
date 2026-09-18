package com.memorylayer.api.ingestion;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentKeys;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.document.KbParsingPath;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.ConflictException;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobRequest;
import software.amazon.awssdk.services.bedrockagent.model.StartIngestionJobResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * S3 {@code ObjectCreated} (via SQS) -> Knowledge Base staging + incremental ingestion.
 * Docs/ARCHITECTURE.md §7.3, as amended by the Phase 4 compatibility spike.
 *
 * <p>Per-record work (stage the file, write its sidecar, flip status) is idempotent under SQS
 * at-least-once redelivery: a document that has already progressed past {@code UPLOADED} is
 * skipped entirely on a later delivery, rather than re-copied and re-grouped for another
 * ingestion job attempt. Starting the actual ingestion job is where Bedrock's real constraint
 * bites: a Knowledge Base allows only one concurrent ingestion job **total**, across every
 * data source. A single SQS batch can contain files for both the multimodal and text data
 * sources; each is grouped and a job start is attempted independently, so one group's
 * {@link ConflictException} never blocks or rolls back another group that already succeeded
 * in the same invocation. When {@link ConflictException} (or any other failure — see
 * {@code catch (RuntimeException e)} below) is thrown, only that group's SQS messages are
 * reported as batch item failures so they retry later via the queue's own visibility timeout,
 * and the affected documents are left at {@code UPLOADED} rather than marked {@code FAILED}.
 * See {@code Infra/.../IngestionStack} for why the queue's retry budget is sized generously
 * enough to absorb this.
 */
public class IngestionCoordinatorHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DocumentRepository documentRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final KbStagingService kbStagingService;
    private final BedrockAgentClient bedrockAgentClient;
    private final String knowledgeBaseId;
    private final String multimodalDataSourceId;
    private final String textDataSourceId;

    public IngestionCoordinatorHandler() {
        Region region = resolveRegion();
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        S3Client s3Client = S3Client.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();

        String tableName = System.getenv("TABLE_NAME");
        this.documentRepository = new DocumentRepository(enhancedClient);
        this.ingestionJobRepository = new IngestionJobRepository(enhancedClient, tableName);
        this.kbStagingService = new KbStagingService(s3Client, System.getenv("UPLOADS_BUCKET_NAME"));
        this.bedrockAgentClient = BedrockAgentClient.builder()
                .region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.knowledgeBaseId = System.getenv("KNOWLEDGE_BASE_ID");
        this.multimodalDataSourceId = System.getenv("MULTIMODAL_DATA_SOURCE_ID");
        this.textDataSourceId = System.getenv("TEXT_DATA_SOURCE_ID");
    }

    private static Region resolveRegion() {
        String region = System.getenv("AWS_REGION");
        return Region.of(region != null && !region.isBlank() ? region : "ap-south-1");
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        Map<KbParsingPath, List<Document>> documentsByPath = new EnumMap<>(KbParsingPath.class);
        Map<KbParsingPath, List<String>> messageIdsByPath = new EnumMap<>(KbParsingPath.class);

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                stageMessage(message, documentsByPath, messageIdsByPath);
            } catch (Exception e) {
                context.getLogger().log("Failed to process message " + message.getMessageId() + ": " + e);
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        for (Map.Entry<KbParsingPath, List<Document>> entry : documentsByPath.entrySet()) {
            KbParsingPath path = entry.getKey();
            List<Document> documents = entry.getValue();
            String dataSourceId = path == KbParsingPath.MULTIMODAL ? multimodalDataSourceId : textDataSourceId;

            try {
                startJobFor(dataSourceId, documents);
            } catch (ConflictException e) {
                // Expected: only one ingestion job may run per Knowledge Base at a time —
                // BDA and text data sources share the same one-job budget. Leave these
                // documents at UPLOADED and retry the messages later; do not treat this as a
                // failure worth exhausting the DLQ retry budget over.
                context.getLogger().log("Ingestion job busy for data source " + dataSourceId
                        + "; " + documents.size() + " document(s) will retry.");
                failMessages(messageIdsByPath.get(path), failures);
            } catch (RuntimeException e) {
                // Any other failure (throttling, a transient AWS error, an unexpected
                // DynamoDB write failure while marking documents INDEXING, etc.) must be
                // scoped to just this path's messages, not allowed to propagate out of
                // handleRequest — an uncaught exception here would fail the ENTIRE batch,
                // including other data sources' groups that already started successfully
                // earlier in this same invocation, causing needless reprocessing (and, absent
                // the idempotency guard in stageMessage, a redundant duplicate ingestion job
                // for documents that already have one in flight).
                context.getLogger().log("Failed to start ingestion job for data source " + dataSourceId
                        + ": " + e + "; " + documents.size() + " document(s) will retry.");
                failMessages(messageIdsByPath.get(path), failures);
            }
        }

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    private static void failMessages(List<String> messageIds, List<SQSBatchResponse.BatchItemFailure> failures) {
        for (String messageId : messageIds) {
            failures.add(SQSBatchResponse.BatchItemFailure.builder()
                    .withItemIdentifier(messageId)
                    .build());
        }
    }

    private void stageMessage(SQSEvent.SQSMessage message,
                               Map<KbParsingPath, List<Document>> documentsByPath,
                               Map<KbParsingPath, List<String>> messageIdsByPath) throws UnsupportedEncodingException {
        for (S3ObjectRef ref : parseS3Records(message.getBody())) {
            DocumentKeys.ParsedSourceKey parsed = DocumentKeys.parseSourceKey(ref.key());
            if (parsed == null) {
                continue;
            }

            Optional<Document> maybeDocument =
                    documentRepository.findByUserAndDocumentId(parsed.userId(), parsed.documentId());
            if (maybeDocument.isEmpty()) {
                continue;
            }
            Document document = maybeDocument.get();

            // Idempotency guard: a redelivered message whose document already progressed
            // past UPLOADED (INDEXING from an earlier successful attempt in this invocation
            // or a prior one, or already READY/FAILED) has nothing left to do here. Skipping
            // it avoids both a wasted re-copy of an already-staged file and — more
            // importantly — a redundant StartIngestionJob attempt for content that already
            // has a job covering it.
            if (document.getStatus() != DocumentStatus.UPLOAD_PENDING
                    && document.getStatus() != DocumentStatus.UPLOADED) {
                continue;
            }

            markUploaded(document);

            KbParsingPath path = KbParsingPath.fromFileName(document.getFileName());
            kbStagingService.stage(document, path);

            documentsByPath.computeIfAbsent(path, p -> new ArrayList<>()).add(document);
            messageIdsByPath.computeIfAbsent(path, p -> new ArrayList<>()).add(message.getMessageId());
        }
    }

    /** Idempotency guard: only ever advances UPLOAD_PENDING -> UPLOADED, never regresses a
     * document a later stage (or a duplicate/late redelivery) has already moved further. */
    private void markUploaded(Document document) {
        if (document.getStatus() == DocumentStatus.UPLOAD_PENDING) {
            String now = Instant.now().toString();
            document.setStatus(DocumentStatus.UPLOADED);
            document.setUploadedAt(now);
            document.setUpdatedAt(now);
            documentRepository.save(document);
        }
    }

    private void startJobFor(String dataSourceId, List<Document> documents) {
        StartIngestionJobResponse response = bedrockAgentClient.startIngestionJob(StartIngestionJobRequest.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .dataSourceId(dataSourceId)
                .clientToken(UUID.randomUUID().toString())
                .build());

        String jobId = response.ingestionJob().ingestionJobId();
        String now = Instant.now().toString();
        // "userId#documentId" — the reconciler needs userId too for a direct lookup
        // (DocumentRepository has no documentId-only index; see IngestionJob's Javadoc).
        List<String> documentRefs = documents.stream()
                .map(document -> document.getUserId() + "#" + document.getDocumentId())
                .toList();

        // Deployment-time bug found and fixed: this job record used to be saved only AFTER
        // marking every document INDEXING. StartIngestionJob above is a real, non-retractable
        // side effect — once it returns, a Bedrock job is genuinely running whether or not
        // anything below succeeds. With Lambda concurrency no longer reserved to 1 (see
        // IngestionStack's Javadoc), two invocations can race to update the same Document,
        // and the loser's DynamoDB write throws before ever reaching the job-record save,
        // orphaning a real running job with NO tracking record — the reconciler can never
        // find it, and its documents (whichever ones got marked INDEXING before the failure)
        // would stay INDEXING forever. Saving the job record FIRST means the reconciler can
        // always resolve it even if a document-level update below fails.
        IngestionJob job = new IngestionJob();
        job.setPk(IngestionJob.PARTITION_KEY);
        job.setSk(IngestionJobRepository.sortKey(jobId));
        job.setJobId(jobId);
        job.setDataSourceId(dataSourceId);
        job.setStatus(IngestionJobStatus.STARTING);
        job.setDocumentIds(documentRefs);
        job.setStartedAt(now);
        job.setUpdatedAt(now);
        ingestionJobRepository.save(job);

        for (Document document : documents) {
            markIndexing(document, jobId, now);
        }
    }

    /** Guard mirrors markUploaded: only ever advances UPLOADED -> INDEXING. Retries once on a
     * concurrent-write conflict (see startJobFor's Javadoc) by re-reading the document's
     * current version rather than losing the update entirely. */
    private void markIndexing(Document document, String jobId, String now) {
        if (document.getStatus() != DocumentStatus.UPLOADED) {
            return;
        }
        document.setStatus(DocumentStatus.INDEXING);
        document.setIngestionJobId(jobId);
        document.setUpdatedAt(now);
        try {
            documentRepository.save(document);
        } catch (ConditionalCheckFailedException e) {
            documentRepository.findByUserAndDocumentId(document.getUserId(), document.getDocumentId())
                    .filter(fresh -> fresh.getStatus() == DocumentStatus.UPLOADED)
                    .ifPresent(fresh -> {
                        fresh.setStatus(DocumentStatus.INDEXING);
                        fresh.setIngestionJobId(jobId);
                        fresh.setUpdatedAt(now);
                        documentRepository.save(fresh);
                    });
        }
    }

    private record S3ObjectRef(String bucket, String key) {
    }

    private static List<S3ObjectRef> parseS3Records(String messageBody) throws UnsupportedEncodingException {
        List<S3ObjectRef> refs = new ArrayList<>();
        JsonNode root;
        try {
            root = MAPPER.readTree(messageBody);
        } catch (Exception e) {
            return refs;
        }
        JsonNode records = root.get("Records");
        if (records == null || !records.isArray()) {
            return refs;
        }
        for (JsonNode record : records) {
            JsonNode s3 = record.get("s3");
            if (s3 == null) {
                continue;
            }
            String bucket = s3.path("bucket").path("name").asText(null);
            String rawKey = s3.path("object").path("key").asText(null);
            if (bucket == null || rawKey == null) {
                continue;
            }
            // S3 event notification keys are URL-encoded with '+' for spaces.
            String key = URLDecoder.decode(rawKey.replace("+", "%2B"), StandardCharsets.UTF_8.name());
            refs.add(new S3ObjectRef(bucket, key));
        }
        return refs;
    }
}
