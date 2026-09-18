package com.memorylayer.api.controller;

import com.amazonaws.serverless.proxy.RequestReader;
import com.memorylayer.api.aws.S3PresignService;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.security.AuthorizedRequestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.cloud.function.serverless.web.ServerlessAutoConfiguration")
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private S3PresignService s3PresignService;

    @Test
    void initializesUploadCreatesPendingDocumentAndReturnsPresignedUrl() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://uploads-bucket.s3.ap-south-1.amazonaws.com/signed").toURL());
        when(presigned.expiration()).thenReturn(Instant.parse("2026-09-18T04:00:00Z"));
        when(s3PresignService.presignUpload(anyString(), anyString())).thenReturn(presigned);

        String requestBody = """
                {
                  "files": [
                    { "clientFileId": "browser-1", "fileName": "internship offer.pdf", "contentType": "application/pdf", "sizeBytes": 2481934 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/uploads")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub("user-abc"))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploads[0].clientFileId").value("browser-1"))
                .andExpect(jsonPath("$.uploads[0].status").value("UPLOAD_PENDING"))
                .andExpect(jsonPath("$.uploads[0].documentId").isNotEmpty())
                .andExpect(jsonPath("$.uploads[0].upload.method").value("PUT"))
                .andExpect(jsonPath("$.uploads[0].upload.url").value("https://uploads-bucket.s3.ap-south-1.amazonaws.com/signed"));

        var captor = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo("user-abc");
        assertThat(saved.getStatus().name()).isEqualTo("UPLOAD_PENDING");
        assertThat(saved.getPk()).isEqualTo("USER#user-abc");
        assertThat(saved.getS3Key()).startsWith("users/user-abc/documents/").endsWith("/original/internship_offer.pdf");
        // Space is not a safe S3-key character; sanitization must have replaced it.
        assertThat(saved.getS3Key()).doesNotContain(" ");
    }

    @Test
    void missingFileNameReturns400() throws Exception {
        String requestBody = """
                { "files": [ { "clientFileId": "browser-1", "contentType": "application/pdf", "sizeBytes": 100 } ] }
                """;

        mockMvc.perform(post("/api/v1/uploads")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub("user-abc"))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void bulkUploadCreatesOneDocumentPerFile() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://uploads-bucket.s3.ap-south-1.amazonaws.com/signed").toURL());
        when(presigned.expiration()).thenReturn(Instant.parse("2026-09-18T04:00:00Z"));
        when(s3PresignService.presignUpload(anyString(), anyString())).thenReturn(presigned);

        String requestBody = """
                {
                  "files": [
                    { "clientFileId": "a", "fileName": "one.pdf", "contentType": "application/pdf", "sizeBytes": 10 },
                    { "clientFileId": "b", "fileName": "two.png", "contentType": "image/png", "sizeBytes": 20 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/uploads")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub("user-abc"))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploads.length()").value(2));

        verify(documentRepository, org.mockito.Mockito.times(2)).save(any(Document.class));
    }
}
