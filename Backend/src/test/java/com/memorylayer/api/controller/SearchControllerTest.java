package com.memorylayer.api.controller;

import com.amazonaws.serverless.proxy.RequestReader;
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
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.cloud.function.serverless.web.ServerlessAutoConfiguration")
@AutoConfigureMockMvc
class SearchControllerTest {

    private static final String USER_ID = "user-abc";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @MockBean
    private DocumentRepository documentRepository;

    private static Document sampleDocument(String documentId) {
        Document document = new Document();
        document.setPk("USER#" + USER_ID);
        document.setSk("DOC#" + documentId);
        document.setDocumentId(documentId);
        document.setUserId(USER_ID);
        document.setFileName("Screenshot_20260903.png");
        document.setMediaCategory(MediaCategory.IMAGE);
        document.setMimeType("image/png");
        document.setSizeBytes(1024);
        document.setStatus(DocumentStatus.READY);
        document.setCreatedAt("2026-09-18T03:00:00Z");
        document.setUpdatedAt("2026-09-18T03:00:00Z");
        return document;
    }

    private static KnowledgeBaseRetrievalResult chunk(String documentId, String text, double score) {
        return KnowledgeBaseRetrievalResult.builder()
                .content(b -> b.text(text))
                .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                .score(score)
                .build();
    }

    @Test
    void returnsDeduplicatedDocumentCentricResultsWithResolvedMetadata() throws Exception {
        when(bedrockAgentRuntimeClient.retrieve(any(RetrieveRequest.class)))
                .thenReturn(RetrieveResponse.builder()
                        .retrievalResults(chunk("doc-1", "AWS promotional credits available...", 0.87))
                        .build());
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), eq("doc-1")))
                .thenReturn(Optional.of(sampleDocument("doc-1")));

        String requestBody = """
                { "query": "that screenshot about AWS hackathon credits" }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("that screenshot about AWS hackathon credits"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].document.documentId").value("doc-1"))
                .andExpect(jsonPath("$.results[0].document.fileName").value("Screenshot_20260903.png"))
                .andExpect(jsonPath("$.results[0].document.mediaCategory").value("IMAGE"))
                .andExpect(jsonPath("$.results[0].document.mimeType").value("image/png"))
                .andExpect(jsonPath("$.results[0].match.snippet").value("AWS promotional credits available..."))
                .andExpect(jsonPath("$.results[0].match.score").value(0.87));
    }

    @Test
    void missingQueryReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/search")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void clientSuppliedUserIdAndTenantIdAreIgnoredNotUsedForFiltering() throws Exception {
        when(bedrockAgentRuntimeClient.retrieve(any(RetrieveRequest.class)))
                .thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

        // Neither field exists on SearchRequest, so Jackson silently drops them — this test
        // documents/locks in that there is structurally no way for a client to influence the
        // authenticated-user filter via the request body.
        String requestBody = """
                { "query": "test", "userId": "someone-else", "tenantId": "other-tenant" }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());

        // No filters requested, so buildFilter returns the bare userId equals-clause directly
        // (not wrapped in andAll) — and it can only ever be the authenticated sub, since
        // SearchRequest has no field a client could use to influence it.
        var captor = org.mockito.ArgumentCaptor.forClass(RetrieveRequest.class);
        org.mockito.Mockito.verify(bedrockAgentRuntimeClient).retrieve(captor.capture());
        var filter = captor.getValue().retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.equalsValue().key()).isEqualTo("userId");
        assertThat(filter.equalsValue().value().asString()).isEqualTo(USER_ID);
    }

    @Test
    void mediaCategoryFilterIsPassedThroughAsAnAdditiveClause() throws Exception {
        when(bedrockAgentRuntimeClient.retrieve(any(RetrieveRequest.class)))
                .thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

        String requestBody = """
                { "query": "test", "filters": { "mediaCategories": ["IMAGE", "DOCUMENT"] } }
                """;

        mockMvc.perform(post("/api/v1/search")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(RetrieveRequest.class);
        org.mockito.Mockito.verify(bedrockAgentRuntimeClient).retrieve(captor.capture());
        var filter = captor.getValue().retrievalConfiguration().vectorSearchConfiguration().filter();

        // The category filter is additive (andAll), never a replacement for the userId clause.
        assertThat(filter.andAll()).hasSize(2);
        assertThat(filter.andAll().get(0).equalsValue().key()).isEqualTo("userId");
        assertThat(filter.andAll().get(0).equalsValue().value().asString()).isEqualTo(USER_ID);
        assertThat(filter.andAll().get(1).in().key()).isEqualTo("mediaCategory");
        List<String> categoryValues = filter.andAll().get(1).in().value().asList().stream()
                .map(software.amazon.awssdk.core.document.Document::asString)
                .toList();
        assertThat(categoryValues).containsExactly("IMAGE", "DOCUMENT");
    }
}
