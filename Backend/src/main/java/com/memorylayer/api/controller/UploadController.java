package com.memorylayer.api.controller;

import com.memorylayer.api.aws.S3PresignService;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentKeys;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.document.FileNameSanitizer;
import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.dto.UploadFileRequest;
import com.memorylayer.api.dto.UploadInstructions;
import com.memorylayer.api.dto.UploadRequest;
import com.memorylayer.api.dto.UploadResponse;
import com.memorylayer.api.dto.UploadResult;
import com.memorylayer.api.error.InvalidRequestException;
import com.memorylayer.api.security.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Docs/API.md §10-14. Single-file and bulk upload share this one endpoint — a bulk upload
 * is simply {@code files.length > 1}. The Java API never receives file bytes; the browser
 * PUTs directly to S3 using the returned presigned URLs.
 *
 * <p>Per Phase 3 scope: documents are created and stay {@code UPLOAD_PENDING} here. The
 * transition to {@code UPLOADED} happens in Phase 4, driven by the authoritative S3
 * {@code ObjectCreated} event — not by anything the client reports back to this API.
 */
@RestController
public class UploadController {

    private final DocumentRepository documentRepository;
    private final S3PresignService s3PresignService;

    public UploadController(DocumentRepository documentRepository, S3PresignService s3PresignService) {
        this.documentRepository = documentRepository;
        this.s3PresignService = s3PresignService;
    }

    @PostMapping("/api/v1/uploads")
    public UploadResponse initializeUploads(HttpServletRequest request, @RequestBody UploadRequest uploadRequest) {
        String userId = AuthenticatedUserResolver.resolveUserId(request);

        if (uploadRequest == null || uploadRequest.files() == null || uploadRequest.files().isEmpty()) {
            throw new InvalidRequestException("At least one file is required");
        }

        List<UploadResult> results = new ArrayList<>();
        for (UploadFileRequest file : uploadRequest.files()) {
            results.add(initializeSingleUpload(userId, file));
        }
        return new UploadResponse(results);
    }

    private UploadResult initializeSingleUpload(String userId, UploadFileRequest file) {
        if (file.fileName() == null || file.fileName().isBlank()) {
            throw new InvalidRequestException("fileName is required");
        }
        if (file.contentType() == null || file.contentType().isBlank()) {
            throw new InvalidRequestException("contentType is required");
        }

        String documentId = UUID.randomUUID().toString();
        String sanitizedFileName = FileNameSanitizer.sanitize(file.fileName());
        String s3Key = "users/%s/documents/%s/original/%s".formatted(userId, documentId, sanitizedFileName);
        String now = Instant.now().toString();

        Document document = new Document();
        document.setPk(DocumentKeys.userPartitionKey(userId));
        document.setSk(DocumentKeys.documentSortKey(documentId));
        document.setGsi1Pk(DocumentKeys.userPartitionKey(userId));
        document.setGsi1Sk(DocumentKeys.gsi1SortKey(now, documentId));
        document.setDocumentId(documentId);
        document.setUserId(userId);
        document.setFileName(sanitizedFileName);
        document.setMediaCategory(MediaCategory.fromMimeType(file.contentType()));
        document.setMimeType(file.contentType());
        document.setSizeBytes(file.sizeBytes() == null ? 0 : file.sizeBytes());
        document.setS3Key(s3Key);
        document.setStatus(DocumentStatus.UPLOAD_PENDING);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);

        documentRepository.save(document);

        PresignedPutObjectRequest presigned = s3PresignService.presignUpload(s3Key, file.contentType());

        UploadInstructions instructions = new UploadInstructions(
                "PUT",
                presigned.url().toString(),
                Map.of("Content-Type", file.contentType()),
                presigned.expiration().toString());

        return new UploadResult(file.clientFileId(), documentId, DocumentStatus.UPLOAD_PENDING.name(), instructions);
    }
}
