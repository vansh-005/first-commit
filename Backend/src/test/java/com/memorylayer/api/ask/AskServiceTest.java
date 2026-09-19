package com.memorylayer.api.ask;

import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.dto.AskRequest;
import com.memorylayer.api.dto.AskResponse;
import com.memorylayer.api.error.AskSessionExpiredException;
import com.memorylayer.api.error.InvalidRequestException;
import com.memorylayer.api.error.RetrievalUnavailableException;
import com.memorylayer.api.search.KnowledgeBaseRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.Citation;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateOutput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievedReference;
import software.amazon.awssdk.services.bedrockagentruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockagentruntime.model.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskServiceTest {

    private static final String USER_ID = "user-abc";
    private static final String NO_ANSWER = "I couldn't find anything in your memories that answers that.";

    @Mock
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Mock
    private KnowledgeBaseRetriever retriever;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AskSessionRepository askSessionRepository;

    private AskService askService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        askService = new AskService(bedrockAgentRuntimeClient, retriever, documentRepository, askSessionRepository);
    }

    private static Document sampleDocument(String documentId, String fileName) {
        Document document = new Document();
        document.setPk("USER#" + USER_ID);
        document.setSk("DOC#" + documentId);
        document.setDocumentId(documentId);
        document.setUserId(USER_ID);
        document.setFileName(fileName);
        document.setMediaCategory(MediaCategory.DOCUMENT);
        document.setMimeType("application/pdf");
        document.setStatus(DocumentStatus.READY);
        return document;
    }

    private static Document sampleDocument(String documentId) {
        return sampleDocument(documentId, "internship-offer.pdf");
    }

    /** A chunk that already passed the relevance gate (the gate itself lives in the retriever). */
    private static KnowledgeBaseRetrievalResult relevantChunk(String documentId) {
        return KnowledgeBaseRetrievalResult.builder()
                .content(RetrievalResultContent.builder().text("relevant excerpt").build())
                .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                .score(0.8)
                .build();
    }

    private void preflightFinds(String... documentIds) {
        when(retriever.retrieveRelevant(any(RetrievalFilter.class), any(String.class), anyInt()))
                .thenReturn(java.util.Arrays.stream(documentIds).map(AskServiceTest::relevantChunk).toList());
    }

    private void preflightFindsNothing() {
        when(retriever.retrieveRelevant(any(RetrievalFilter.class), any(String.class), anyInt())).thenReturn(List.of());
    }

    private static RetrieveAndGenerateResponse responseWithCitation(String sessionId, String documentId, String answer) {
        Citation citation = Citation.builder()
                .retrievedReferences(RetrievedReference.builder()
                        .content(RetrievalResultContent.builder().text("relevant excerpt").build())
                        .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                        .build())
                .build();
        return RetrieveAndGenerateResponse.builder()
                .sessionId(sessionId)
                .output(RetrieveAndGenerateOutput.builder().text(answer).build())
                .citations(citation)
                .build();
    }

    private static RetrieveAndGenerateResponse responseWithCitation(String sessionId, String documentId) {
        return responseWithCitation(sessionId, documentId, "the generated answer");
    }

    // ---------------------------------------------------------------- validation / sessions

    @Test
    void rejectsABlankQuestion() {
        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("  ", null)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoBedrockCall();
    }

    @Test
    void aSessionIdNotFoundUnderThisUsersPartitionExpiresWithoutEverCallingBedrockOrRetrieving() {
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "someone-elses-or-expired-session"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("continue?", "someone-elses-or-expired-session")))
                .isInstanceOf(AskSessionExpiredException.class);

        verifyNoBedrockCall();
        verify(retriever, never()).retrieveRelevant(any(), any(), anyInt());
    }

    // ---------------------------------------------------------------- relevance preflight

    @Test
    void whenNothingRelevantExistsItReturnsTheDeterministicNoAnswerWithZeroCitationsAndNeverCallsGeneration() {
        // "How much AWS credit did I have?" when no matching memory exists.
        preflightFindsNothing();

        AskResponse response = askService.ask(USER_ID, new AskRequest("How much AWS credit did I have?", null));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
        assertThat(response.sessionId()).isNull();
        verifyNoBedrockCall();
        verify(askSessionRepository, never()).save(any());
    }

    @Test
    void thePreflightUsesTheAuthenticatedUsersTenantFilterAndTheQuestion() {
        preflightFindsNothing();

        askService.ask(USER_ID, new AskRequest("some question", null));

        ArgumentCaptor<RetrievalFilter> filter = ArgumentCaptor.forClass(RetrievalFilter.class);
        verify(retriever).retrieveRelevant(filter.capture(), eq("some question"), anyInt());
        assertThat(filter.getValue().equalsValue().key()).isEqualTo("userId");
        assertThat(filter.getValue().equalsValue().value().asString()).isEqualTo(USER_ID);
    }

    @Test
    void relevantContextRunsGenerationWithTheCustomPromptAndRestrictsItToTheRelevantDocumentsOfThisUser() {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-1"));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        askService.ask(USER_ID, new AskRequest("What did my offer say?", null));

        ArgumentCaptor<RetrieveAndGenerateRequest> captor = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(captor.capture());
        var kb = captor.getValue().retrieveAndGenerateConfiguration().knowledgeBaseConfiguration();

        // Cross-user isolation: the tenant clause is first and the documentId clause only narrows.
        RetrievalFilter filter = kb.retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.andAll()).hasSize(2);
        assertThat(filter.andAll().get(0).equalsValue().key()).isEqualTo("userId");
        assertThat(filter.andAll().get(0).equalsValue().value().asString()).isEqualTo(USER_ID);
        assertThat(filter.andAll().get(1).in().key()).isEqualTo("documentId");
        assertThat(filter.andAll().get(1).in().value().asList()).extracting(d -> d.asString()).containsExactly("doc-1");

        // The custom prompt, with the placeholders AWS requires (see AskPrompt).
        String template = kb.generationConfiguration().promptTemplate().textPromptTemplate();
        assertThat(template)
                .contains("$search_results$")
                .contains("$output_format_instructions$")
                .contains("deliberately uploaded")
                .contains("Answer ONLY from the search results")
                .contains(NO_ANSWER);
    }

    @Test
    void onlyCitationsFromDocumentsThatPassedTheRelevanceGateAreReturned() {
        preflightFinds("doc-relevant");
        // Generation attaches a reference to a document that did NOT pass the gate.
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("s1", "doc-irrelevant"));
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), any())).thenReturn(Optional.of(sampleDocument("x")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("question", null));

        assertThat(response.citations()).isEmpty();
        verify(documentRepository, never()).findByUserAndDocumentId(any(), eq("doc-irrelevant"));
    }

    @Test
    void aRefusalFromTheModelDropsItsCitationsEvenThoughContextWasRelevant() {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("s1", "doc-1", NO_ANSWER));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("something the document does not say", null));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void aKnownContentQuestionReturnsTheGroundedAnswerWithOnlyItsRelevantSource() {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("s1", "doc-1", "Relocation support of Rs 40,000 is paid on joining."));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("What did my offer say about relocation?", null));

        assertThat(response.answer()).contains("40,000");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentId()).isEqualTo("doc-1");
    }

    // ---------------------------------------------------------------- existence / find path

    @Test
    void aFindQuestionIsAnsweredFromRealFilenamesWithoutCallingGeneration() {
        // "do I have numerical methods assignment?" - the model can't see filenames, so Ask must.
        preflightFinds("doc-nm");
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("do I have numerical methods assignment?", null));

        assertThat(response.answer()).contains("NumericalMethods_Assignment1.pdf").contains("1 file");
        assertThat(response.answer()).doesNotContain("couldn't find");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).fileName()).isEqualTo("NumericalMethods_Assignment1.pdf");
        verifyNoBedrockCall();
    }

    @Test
    void aFindQuestionWithNothingRelevantStillGivesTheNoAnswer() {
        preflightFindsNothing();

        AskResponse response = askService.ask(USER_ID, new AskRequest("do I have a passport scan?", null));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
        verifyNoBedrockCall();
    }

    @Test
    void aFindQuestionListsAtMostThreeMatchingFiles() {
        preflightFinds("d1", "d2", "d3", "d4");
        for (String id : List.of("d1", "d2", "d3", "d4")) {
            when(documentRepository.findByUserAndDocumentId(USER_ID, id)).thenReturn(Optional.of(sampleDocument(id, id + ".pdf")));
        }

        AskResponse response = askService.ask(USER_ID, new AskRequest("Is there a lease document?", null));

        assertThat(response.citations()).hasSize(3);
        assertThat(response.answer()).contains("3 files").contains("d1.pdf").contains("d3.pdf").doesNotContain("d4.pdf");
    }

    // ---------------------------------------------------------------- sessions on the generation path

    @Test
    void firstTurnCreatesANewApplicationSessionAndNeverSendsABedrockSessionId() {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-1"));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("What did my offer say?", null));

        ArgumentCaptor<RetrieveAndGenerateRequest> captor = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(captor.capture());
        assertThat(captor.getValue().sessionId()).isNull();

        assertThat(response.answer()).isEqualTo("the generated answer");
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.sessionId()).isNotEqualTo("bedrock-session-xyz");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentId()).isEqualTo("doc-1");
        assertThat(response.citations().get(0).citationId()).isEqualTo("c1");

        // The raw Bedrock session ID must never reach the response - only our own opaque one.
        verify(askSessionRepository).save(argThatSessionMapsBedrockId("bedrock-session-xyz"));
    }

    @Test
    void followUpResolvesTheBedrockSessionUnderTheAuthenticatedUsersPartitionAndReusesTheSameApplicationSessionId() {
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("bedrock-session-xyz");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-1"));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("Was there a repayment condition?", "app-session-1"));

        ArgumentCaptor<RetrieveAndGenerateRequest> captor = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("bedrock-session-xyz");

        assertThat(response.sessionId()).isEqualTo("app-session-1");
    }

    @Test
    void aFollowUpThatDoesNotMatchOnItsOwnStillReachesGenerationWithTheTenantFilterOnly() {
        // "and the stipend?" only makes sense with conversation history, so it scores low standalone.
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("bedrock-session-xyz");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        preflightFindsNothing();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-1", "The monthly stipend is Rs 60,000."));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("and the stipend?", "app-session-1"));

        ArgumentCaptor<RetrieveAndGenerateRequest> captor = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(captor.capture());
        RetrievalFilter filter = captor.getValue().retrieveAndGenerateConfiguration().knowledgeBaseConfiguration()
                .retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.equalsValue().key()).isEqualTo("userId");
        assertThat(filter.equalsValue().value().asString()).isEqualTo(USER_ID);
        assertThat(response.answer()).contains("60,000");
        assertThat(response.sessionId()).isEqualTo("app-session-1");
    }

    @Test
    void anUnmatchedFollowUpThatTheModelRefusesReturnsNoCitations() {
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("bedrock-session-xyz");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        preflightFindsNothing();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-1", NO_ANSWER));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("what about the moon?", "app-session-1"));

        assertThat(response.citations()).isEmpty();
    }

    @Test
    void bedrockRejectingAnExistingSessionAsInvalidExpiresTheMappingRatherThanRetryingTransparently() {
        // Exact wording confirmed live during the Phase 6 spike for an invalid/expired session.
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("stale-bedrock-session");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(ValidationException.builder()
                        .message("Session with Id stale-bedrock-session is not valid. Please check and try again.")
                        .build());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("continue?", "app-session-1")))
                .isInstanceOf(AskSessionExpiredException.class);

        verify(askSessionRepository).delete(USER_ID, "app-session-1");
        // Only ever the one call - no transparent contextual retry without history.
        verify(bedrockAgentRuntimeClient, times(1)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    @Test
    void anUnrelatedValidationExceptionOnAnExistingSessionDoesNotExpireOrDeleteTheSession() {
        // Regression test (post-approval correction): a ValidationException that has nothing
        // to do with session validity must not destroy an otherwise-healthy session mapping.
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("healthy-bedrock-session");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(ValidationException.builder()
                        .message("The text field must not exceed 1000 characters.")
                        .build());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("continue?", "app-session-1")))
                .isInstanceOf(InvalidRequestException.class);

        verify(askSessionRepository, never()).delete(any(), any());
    }

    // ---------------------------------------------------------------- failure mapping

    @Test
    void throttlingIsRetryable() {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(ThrottlingException.builder().message("busy").build());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("question", null)))
                .isInstanceOf(RetrievalUnavailableException.class)
                .satisfies(e -> assertThat(((RetrievalUnavailableException) e).isRetryable()).isTrue());
    }

    @Test
    void otherUpstreamFailuresAreNotRetryable() {
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(software.amazon.awssdk.core.exception.SdkClientException.create("boom"));

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("question", null)))
                .isInstanceOf(RetrievalUnavailableException.class)
                .satisfies(e -> assertThat(((RetrievalUnavailableException) e).isRetryable()).isFalse());
    }

    @Test
    void aFailingPreflightSurfacesAsUnavailableAndNeverFallsThroughToGeneration() {
        when(retriever.retrieveRelevant(any(RetrievalFilter.class), any(String.class), anyInt()))
                .thenThrow(new RetrievalUnavailableException("busy", true));

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("question", null)))
                .isInstanceOf(RetrievalUnavailableException.class);
        verifyNoBedrockCall();
    }

    @Test
    void skipsACitationWhoseDocumentCannotBeResolvedForThisUser() {
        preflightFinds("doc-owned-by-someone-else");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-owned-by-someone-else"));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-owned-by-someone-else")).thenReturn(Optional.empty());

        AskResponse response = askService.ask(USER_ID, new AskRequest("question", null));

        assertThat(response.citations()).isEmpty();
    }

    private void verifyNoBedrockCall() {
        verify(bedrockAgentRuntimeClient, never()).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    private static AskSession argThatSessionMapsBedrockId(String expectedBedrockSessionId) {
        return org.mockito.ArgumentMatchers.argThat(session -> expectedBedrockSessionId.equals(session.getBedrockSessionId()));
    }
}
