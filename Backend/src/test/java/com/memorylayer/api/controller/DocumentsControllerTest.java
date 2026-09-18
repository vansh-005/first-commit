package com.memorylayer.api.controller;

import com.amazonaws.serverless.proxy.RequestReader;
import com.memorylayer.api.aws.S3PresignService;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.security.AuthorizedRequestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.cloud.function.serverless.web.ServerlessAutoConfiguration")
@AutoConfigureMockMvc
class DocumentsControllerTest {

    private static final String USER_ID = "user-abc";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private S3PresignService s3PresignService;

    private static Document sampleDocument(String userId, String documentId) {
        Document document = new Document();
        document.setPk("USER#" + userId);
        document.setSk("DOC#" + documentId);
        document.setDocumentId(documentId);
        document.setUserId(userId);
        document.setFileName("internship-offer.pdf");
        document.setMediaCategory(MediaCategory.DOCUMENT);
        document.setMimeType("application/pdf");
        document.setSizeBytes(2481934);
        document.setS3Key("users/" + userId + "/documents/" + documentId + "/original/internship-offer.pdf");
        document.setStatus(DocumentStatus.UPLOAD_PENDING);
        document.setCreatedAt("2026-09-18T03:00:00Z");
        document.setUpdatedAt("2026-09-18T03:00:00Z");
        return document;
    }

    @Test
    void listsDocumentsForAuthenticatedUser() throws Exception {
        Document doc = sampleDocument(USER_ID, "doc-1");
        when(documentRepository.queryByUserChronological(eq(USER_ID), anyInt(), any()))
                .thenReturn(new DocumentRepository.DocumentPage(List.of(doc), null));

        mockMvc.perform(get("/api/v1/documents")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.items[0].fileName").value("internship-offer.pdf"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void getDocumentReturns404WhenNotOwnedByAuthenticatedUser() throws Exception {
        // Repository lookup is scoped to USER_ID's own partition, so a document belonging
        // to someone else — or one that never existed — is indistinguishable: not found.
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), eq("someone-elses-doc")))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/someone-elses-doc")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void getDocumentReturns200ForOwnedDocument() throws Exception {
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), eq("doc-1")))
                .thenReturn(Optional.of(sampleDocument(USER_ID, "doc-1")));

        mockMvc.perform(get("/api/v1/documents/doc-1")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.status").value("UPLOAD_PENDING"));
    }

    @Test
    void accessUrlSignsGetForOwnedDocument() throws Exception {
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), eq("doc-1")))
                .thenReturn(Optional.of(sampleDocument(USER_ID, "doc-1")));

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://uploads-bucket.s3.ap-south-1.amazonaws.com/download").toURL());
        when(presigned.expiration()).thenReturn(Instant.parse("2026-09-18T04:00:00Z"));
        when(s3PresignService.presignDownload(anyString())).thenReturn(presigned);

        mockMvc.perform(get("/api/v1/documents/doc-1/access-url")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.url").value("https://uploads-bucket.s3.ap-south-1.amazonaws.com/download"));
    }

    @Test
    void accessUrlRefusesToSignIfS3KeyEverEndedUpOutsideUsersPrefix() throws Exception {
        // Defense in depth (Docs/DATA_MODEL.md §11): even if a document row somehow had a
        // foreign S3 key, the access-url endpoint must still refuse rather than sign it.
        Document mismatched = sampleDocument(USER_ID, "doc-1");
        mismatched.setS3Key("users/someone-else/documents/doc-1/original/file.pdf");
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), eq("doc-1")))
                .thenReturn(Optional.of(mismatched));

        mockMvc.perform(get("/api/v1/documents/doc-1/access-url")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID)))
                .andExpect(status().isNotFound());
    }
}
