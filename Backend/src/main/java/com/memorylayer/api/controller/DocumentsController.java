package com.memorylayer.api.controller;

import com.memorylayer.api.aws.S3PresignService;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.dto.AccessUrlResponse;
import com.memorylayer.api.dto.DocumentSummary;
import com.memorylayer.api.dto.DocumentsListResponse;
import com.memorylayer.api.error.DocumentNotFoundException;
import com.memorylayer.api.security.AuthenticatedUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.util.List;

/** Docs/API.md §15-17. Ownership is structural: every lookup is scoped to
 * {@code USER#<authenticated sub>}, so a foreign documentId simply doesn't resolve. */
@RestController
public class DocumentsController {

    private static final int DEFAULT_PAGE_SIZE = 30;
    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentRepository documentRepository;
    private final S3PresignService s3PresignService;

    public DocumentsController(DocumentRepository documentRepository, S3PresignService s3PresignService) {
        this.documentRepository = documentRepository;
        this.s3PresignService = s3PresignService;
    }

    @GetMapping("/api/v1/documents")
    public DocumentsListResponse listDocuments(HttpServletRequest request,
                                                @RequestParam(required = false) Integer limit,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) MediaCategory category,
                                                @RequestParam(required = false) DocumentStatus status) {
        String userId = AuthenticatedUserResolver.resolveUserId(request);
        int pageSize = (limit == null || limit <= 0) ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);

        DocumentRepository.DocumentPage page = documentRepository.queryByUserChronological(userId, pageSize, cursor);

        // Docs/DATA_MODEL.md AP3: for MVP-sized libraries, filter in application code rather
        // than adding more GSIs; nextCursor still reflects the underlying unfiltered page.
        List<DocumentSummary> items = page.items().stream()
                .filter(doc -> category == null || doc.getMediaCategory() == category)
                .filter(doc -> status == null || doc.getStatus() == status)
                .map(DocumentsController::toSummary)
                .toList();

        return new DocumentsListResponse(items, page.nextCursor());
    }

    @GetMapping("/api/v1/documents/{documentId}")
    public DocumentSummary getDocument(HttpServletRequest request, @PathVariable String documentId) {
        String userId = AuthenticatedUserResolver.resolveUserId(request);
        Document document = documentRepository.findByUserAndDocumentId(userId, documentId)
                .orElseThrow(DocumentNotFoundException::new);
        return toSummary(document);
    }

    @GetMapping("/api/v1/documents/{documentId}/access-url")
    public AccessUrlResponse getAccessUrl(HttpServletRequest request, @PathVariable String documentId) {
        String userId = AuthenticatedUserResolver.resolveUserId(request);
        Document document = documentRepository.findByUserAndDocumentId(userId, documentId)
                .orElseThrow(DocumentNotFoundException::new);

        // Defense in depth per Docs/DATA_MODEL.md §11: re-verify the S3 key is actually
        // under this user's prefix before ever signing a GET, even though the DynamoDB
        // lookup above is already scoped to the authenticated user's own partition.
        String expectedPrefix = "users/" + userId + "/";
        if (!document.getS3Key().startsWith(expectedPrefix)) {
            throw new DocumentNotFoundException();
        }

        PresignedGetObjectRequest presigned = s3PresignService.presignDownload(document.getS3Key());
        return new AccessUrlResponse(documentId, presigned.url().toString(), presigned.expiration().toString());
    }

    private static DocumentSummary toSummary(Document document) {
        return new DocumentSummary(
                document.getDocumentId(),
                document.getFileName(),
                document.getMediaCategory(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getUploadedAt(),
                document.getUpdatedAt(),
                document.getFailureReason());
    }
}
