package com.memorylayer.api.controller;

import com.amazonaws.serverless.proxy.RequestReader;
import com.memorylayer.api.ask.AskSession;
import com.memorylayer.api.ask.AskSessionRepository;
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
import software.amazon.awssdk.services.bedrockagentruntime.model.Citation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateOutput;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievedReference;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.cloud.function.serverless.web.ServerlessAutoConfiguration")
@AutoConfigureMockMvc
class AskControllerTest {

    private static final String USER_ID = "user-abc";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private AskSessionRepository askSessionRepository;

    private static Document sampleDocument(String documentId) {
        Document document = new Document();
        document.setPk("USER#" + USER_ID);
        document.setSk("DOC#" + documentId);
        document.setDocumentId(documentId);
        document.setUserId(USER_ID);
        document.setFileName("internship-offer.pdf");
        document.setMediaCategory(MediaCategory.DOCUMENT);
        document.setMimeType("application/pdf");
        document.setStatus(DocumentStatus.READY);
        return document;
    }

    /** The preflight Retrieve: one chunk well above the relevance gate for the given document. */
    private void preflightFinds(String documentId) {
        when(bedrockAgentRuntimeClient.retrieve(any(RetrieveRequest.class)))
                .thenReturn(RetrieveResponse.builder()
                        .retrievalResults(KnowledgeBaseRetrievalResult.builder()
                                .content(c -> c.text("relevant excerpt"))
                                .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                                .score(0.85)
                                .build())
                        .build());
    }

    private static RetrieveAndGenerateResponse responseWithCitation(String bedrockSessionId, String documentId) {
        Citation citation = Citation.builder()
                .retrievedReferences(RetrievedReference.builder()
                        .content(RetrievalResultContent.builder().text("relevant excerpt").build())
                        .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                        .build())
                .build();
        return RetrieveAndGenerateResponse.builder()
                .sessionId(bedrockSessionId)
                .output(RetrieveAndGenerateOutput.builder().text("The offer states relocation is covered.").build())
                .citations(citation)
                .build();
    }

    @Test
    void returnsAGroundedAnswerWithCitationsAndNeverExposesTheRawBedrockSessionId() throws Exception {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("raw-bedrock-session-id", "doc-1"));
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), eq("doc-1")))
                .thenReturn(Optional.of(sampleDocument("doc-1")));

        String requestBody = """
                { "question": "What did my internship offer say about relocation?" }
                """;

        mockMvc.perform(post("/api/v1/ask")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The offer states relocation is covered."))
                .andExpect(jsonPath("$.sessionId").value(org.hamcrest.Matchers.not("raw-bedrock-session-id")))
                .andExpect(jsonPath("$.citations.length()").value(1))
                .andExpect(jsonPath("$.citations[0].citationId").value("c1"))
                .andExpect(jsonPath("$.citations[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.citations[0].fileName").value("internship-offer.pdf"))
                .andExpect(jsonPath("$.citations[0].mediaCategory").value("DOCUMENT"))
                .andExpect(jsonPath("$.citations[0].snippet").value("relevant excerpt"))
                .andExpect(jsonPath("$.citations[0].accessUrl").doesNotExist());
    }

    @Test
    void missingQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/ask")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void clientSuppliedUserIdIsIgnoredNotUsedForFiltering() throws Exception {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(RetrieveAndGenerateResponse.builder()
                        .sessionId("s1")
                        .output(RetrieveAndGenerateOutput.builder().text("answer").build())
                        .build());

        // AskRequest has no userId/tenantId field, so Jackson silently drops them — this locks
        // in that there is structurally no way for a client to influence the authenticated-user
        // filter via the request body, same as /search.
        String requestBody = """
                { "question": "test", "userId": "someone-else", "tenantId": "other-tenant" }
                """;

        mockMvc.perform(post("/api/v1/ask")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(captor.capture());
        var filter = captor.getValue().retrieveAndGenerateConfiguration()
                .knowledgeBaseConfiguration().retrievalConfiguration().vectorSearchConfiguration().filter();
        org.assertj.core.api.Assertions.assertThat(filter.andAll().get(0).equalsValue().key()).isEqualTo("userId");
        org.assertj.core.api.Assertions.assertThat(filter.andAll().get(0).equalsValue().value().asString()).isEqualTo(USER_ID);
        // The preflight retrieval is scoped to the same authenticated user.
        var retrieveCaptor = org.mockito.ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(bedrockAgentRuntimeClient).retrieve(retrieveCaptor.capture());
        var preflightFilter = retrieveCaptor.getValue().retrievalConfiguration().vectorSearchConfiguration().filter();
        org.assertj.core.api.Assertions.assertThat(preflightFilter.equalsValue().value().asString()).isEqualTo(USER_ID);
    }

    @Test
    void whenNothingRelevantExistsTheApiReturnsTheGroundedNoAnswerWith200AndNoCitations() throws Exception {
        // Retrieve returns only low-scoring nearest neighbours (e.g. the AWS-credit question when no such memory exists).
        when(bedrockAgentRuntimeClient.retrieve(any(RetrieveRequest.class)))
                .thenReturn(RetrieveResponse.builder()
                        .retrievalResults(KnowledgeBaseRetrievalResult.builder()
                                .content(c -> c.text("unrelated"))
                                .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString("doc-1")))
                                .score(0.5955)
                                .build())
                        .build());

        mockMvc.perform(post("/api/v1/ask")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content("{ \"question\": \"How much AWS credit did I have?\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("I couldn't find anything in your memories that answers that."))
                .andExpect(jsonPath("$.citations.length()").value(0));

        org.mockito.Mockito.verify(bedrockAgentRuntimeClient, org.mockito.Mockito.never())
                .retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    @Test
    void aSessionIdNotBelongingToThisUserReturns409AskSessionExpired() throws Exception {
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "not-mine-or-expired")).thenReturn(Optional.empty());

        String requestBody = """
                { "question": "continue?", "sessionId": "not-mine-or-expired" }
                """;

        mockMvc.perform(post("/api/v1/ask")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ASK_SESSION_EXPIRED"));
    }

    @Test
    void aFollowUpResolvesTheBedrockSessionAndReturnsTheSameApplicationSessionId() throws Exception {
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("raw-bedrock-session-id");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("raw-bedrock-session-id", "doc-1"));
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), eq("doc-1")))
                .thenReturn(Optional.of(sampleDocument("doc-1")));

        String requestBody = """
                { "question": "Was there a repayment condition?", "sessionId": "app-session-1" }
                """;

        mockMvc.perform(post("/api/v1/ask")
                        .requestAttr(RequestReader.HTTP_API_CONTEXT_PROPERTY, AuthorizedRequestSupport.contextForSub(USER_ID))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("app-session-1"));

        var captor = org.mockito.ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().sessionId()).isEqualTo("raw-bedrock-session-id");
    }
}
