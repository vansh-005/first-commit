package com.memorylayer.api.ingestion;

import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the fix confirmed during Phase 7 review: stale-{@code UPLOADED}
 * cleanup must not auto-fail a document that a still-running Bedrock ingestion job actively
 * covers, even though the document's own status write to {@code INDEXING} never landed. See
 * {@link StaleDocumentPolicy#activeJobDocumentRefs} for the full reasoning.
 */
class StaleDocumentCleanupHandlerTest {

    private static final String USER_ID = "user-1";
    private static final String DOCUMENT_ID = "doc-1";

    @Test
    void staleUploadedDocumentCoveredByAnActiveJobIsNotFailed() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        IngestionJobRepository ingestionJobRepository = mock(IngestionJobRepository.class);
        S3Client s3Client = mock(S3Client.class);

        Document staleDocument = staleUploadedDocument();
        when(documentRepository.scanByStatus(DocumentStatus.UPLOAD_PENDING)).thenReturn(List.of());
        when(documentRepository.scanByStatus(DocumentStatus.UPLOADED)).thenReturn(List.of(staleDocument));
        when(documentRepository.scanByStatus(DocumentStatus.INDEXING)).thenReturn(List.of());
        when(ingestionJobRepository.findAll()).thenReturn(List.of(activeJob()));

        StaleDocumentCleanupHandler handler =
                new StaleDocumentCleanupHandler(documentRepository, ingestionJobRepository, s3Client, "uploads-bucket");
        handler.handleRequest(null, null);

        verify(documentRepository, never()).save(any());
    }

    @Test
    void staleUploadedDocumentWithNoTrackedJobIsFailed() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        IngestionJobRepository ingestionJobRepository = mock(IngestionJobRepository.class);
        S3Client s3Client = mock(S3Client.class);

        Document staleDocument = staleUploadedDocument();
        when(documentRepository.scanByStatus(DocumentStatus.UPLOAD_PENDING)).thenReturn(List.of());
        when(documentRepository.scanByStatus(DocumentStatus.UPLOADED)).thenReturn(List.of(staleDocument));
        when(documentRepository.scanByStatus(DocumentStatus.INDEXING)).thenReturn(List.of());
        when(ingestionJobRepository.findAll()).thenReturn(List.of());

        StaleDocumentCleanupHandler handler =
                new StaleDocumentCleanupHandler(documentRepository, ingestionJobRepository, s3Client, "uploads-bucket");
        handler.handleRequest(null, null);

        verify(documentRepository, times(1)).save(staleDocument);
        assert staleDocument.getStatus() == DocumentStatus.FAILED;
    }

    @Test
    void staleUploadedDocumentWithOnlyACompletedJobIsStillFailed() {
        // The active-job guard only covers STARTING/IN_PROGRESS — a job that already finished
        // without the document ever reaching INDEXING is a separate, rarer orphan case the
        // reconciler cannot resolve (it only updates documents still at INDEXING), so the
        // 2-hour auto-fail is the documented, approved behavior here.
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        IngestionJobRepository ingestionJobRepository = mock(IngestionJobRepository.class);
        S3Client s3Client = mock(S3Client.class);

        Document staleDocument = staleUploadedDocument();
        when(documentRepository.scanByStatus(DocumentStatus.UPLOAD_PENDING)).thenReturn(List.of());
        when(documentRepository.scanByStatus(DocumentStatus.UPLOADED)).thenReturn(List.of(staleDocument));
        when(documentRepository.scanByStatus(DocumentStatus.INDEXING)).thenReturn(List.of());

        IngestionJob completedJob = activeJob();
        completedJob.setStatus(IngestionJobStatus.COMPLETE);
        when(ingestionJobRepository.findAll()).thenReturn(List.of(completedJob));

        StaleDocumentCleanupHandler handler =
                new StaleDocumentCleanupHandler(documentRepository, ingestionJobRepository, s3Client, "uploads-bucket");
        handler.handleRequest(null, null);

        verify(documentRepository, times(1)).save(staleDocument);
        assert staleDocument.getStatus() == DocumentStatus.FAILED;
    }

    @Test
    void staleUploadPendingIsFailedOnlyWhenTheS3ObjectIsMissing() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        IngestionJobRepository ingestionJobRepository = mock(IngestionJobRepository.class);
        S3Client s3Client = mock(S3Client.class);

        Document staleDocument = staleUploadPendingDocument();
        when(documentRepository.scanByStatus(DocumentStatus.UPLOAD_PENDING)).thenReturn(List.of(staleDocument));
        when(documentRepository.scanByStatus(DocumentStatus.UPLOADED)).thenReturn(List.of());
        when(documentRepository.scanByStatus(DocumentStatus.INDEXING)).thenReturn(List.of());
        when(ingestionJobRepository.findAll()).thenReturn(List.of());
        when(s3Client.headObject((HeadObjectRequest) any())).thenThrow(NoSuchKeyException.builder().build());

        StaleDocumentCleanupHandler handler =
                new StaleDocumentCleanupHandler(documentRepository, ingestionJobRepository, s3Client, "uploads-bucket");
        handler.handleRequest(null, null);

        verify(documentRepository, times(1)).save(staleDocument);
        assert staleDocument.getStatus() == DocumentStatus.FAILED;
    }

    private static Document staleUploadedDocument() {
        Document document = new Document();
        document.setUserId(USER_ID);
        document.setDocumentId(DOCUMENT_ID);
        document.setStatus(DocumentStatus.UPLOADED);
        String threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS).toString();
        document.setUploadedAt(threeHoursAgo);
        document.setUpdatedAt(threeHoursAgo);
        return document;
    }

    private static Document staleUploadPendingDocument() {
        Document document = new Document();
        document.setUserId(USER_ID);
        document.setDocumentId(DOCUMENT_ID);
        document.setStatus(DocumentStatus.UPLOAD_PENDING);
        document.setS3Key("users/" + USER_ID + "/documents/" + DOCUMENT_ID + "/original/file.pdf");
        String oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        document.setCreatedAt(oneHourAgo);
        document.setUpdatedAt(oneHourAgo);
        return document;
    }

    private static IngestionJob activeJob() {
        IngestionJob job = new IngestionJob();
        job.setPk(IngestionJob.PARTITION_KEY);
        job.setSk(IngestionJobRepository.sortKey("job-1"));
        job.setJobId("job-1");
        job.setDataSourceId("data-source-1");
        job.setStatus(IngestionJobStatus.IN_PROGRESS);
        job.setDocumentIds(List.of(USER_ID + "#" + DOCUMENT_ID));
        job.setStartedAt(Instant.now().minus(3, ChronoUnit.HOURS).toString());
        job.setUpdatedAt(Instant.now().toString());
        return job;
    }
}
