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

        assertThat(response.sessionId()).isNotBlank();
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

    // ---------------------------------------------------------------- conversational document context

    private static AskSession session(String applicationSessionId, String bedrockSessionId, String... contextDocumentIds) {
        AskSession session = new AskSession();
        session.setApplicationSessionId(applicationSessionId);
        session.setUserId(USER_ID);
        session.setBedrockSessionId(bedrockSessionId);
        session.setContextDocumentIds(contextDocumentIds.length == 0 ? null : List.of(contextDocumentIds));
        return session;
    }

    /** Turn 1 finds the file; turn 2 asks {@code followUp} in the same conversation. Returns turn 2's response. */
    private AskResponse findFileThenAsk(String followUp) {
        // --- turn 1: "do I have a numerical methods assignment?"
        preflightFinds("doc-nm");
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));

        AskResponse found = askService.ask(USER_ID, new AskRequest("do I have a numerical methods assignment?", null));

        ArgumentCaptor<AskSession> saved = ArgumentCaptor.forClass(AskSession.class);
        verify(askSessionRepository).save(saved.capture());
        AskSession stored = saved.getValue();
        // No generation happened, yet the conversation now exists and remembers the resolved file (server-side).
        verifyNoBedrockCall();
        assertThat(found.sessionId()).isNotBlank().isEqualTo(stored.getApplicationSessionId());
        assertThat(stored.getUserId()).isEqualTo(USER_ID);
        assertThat(stored.getBedrockSessionId()).isNull();
        assertThat(stored.getContextDocumentIds()).containsExactly("doc-nm");

        // --- turn 2: a deictic follow-up in that conversation
        org.mockito.Mockito.clearInvocations(retriever, askSessionRepository);
        when(askSessionRepository.findByUserAndSessionId(USER_ID, found.sessionId())).thenReturn(Optional.of(stored));
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-1", "doc-nm", "The assignment asks you to ..."));

        AskResponse followed = askService.ask(USER_ID, new AskRequest(followUp, found.sessionId()));

        // Not relevance-gated globally: the global preflight is never even run.
        verify(retriever, never()).retrieveRelevant(any(), any(), anyInt());

        // Retrieval is scoped to (authenticated user AND the resolved file), with no Bedrock session to resume.
        ArgumentCaptor<RetrieveAndGenerateRequest> request = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        // Exactly ONE generation call (verify defaults to times(1)) - no failed first attempt before the reliable one.
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(request.capture());
        assertThat(request.getValue().sessionId()).isNull();
        assertThat(request.getValue().input().text()).isEqualTo("Describe the contents of the file. Then: " + followUp);
        RetrievalFilter filter = request.getValue().retrieveAndGenerateConfiguration().knowledgeBaseConfiguration()
                .retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.andAll().get(0).equalsValue().key()).isEqualTo("userId");
        assertThat(filter.andAll().get(0).equalsValue().value().asString()).isEqualTo(USER_ID);
        assertThat(filter.andAll().get(1).in().key()).isEqualTo("documentId");
        assertThat(filter.andAll().get(1).in().value().asList()).extracting(d -> d.asString()).containsExactly("doc-nm");

        // The answer is grounded in, and cites, that file - under the SAME application session id.
        assertThat(followed.answer()).isNotEqualTo(NO_ANSWER);
        assertThat(followed.citations()).extracting(c -> c.fileName()).containsExactly("NumericalMethods_Assignment1.pdf");
        assertThat(followed.sessionId()).isEqualTo(found.sessionId());

        // The Bedrock session id is saved into that same AskSession, and the context survives.
        ArgumentCaptor<AskSession> resaved = ArgumentCaptor.forClass(AskSession.class);
        verify(askSessionRepository).save(resaved.capture());
        assertThat(resaved.getValue().getApplicationSessionId()).isEqualTo(found.sessionId());
        assertThat(resaved.getValue().getBedrockSessionId()).isEqualTo("bedrock-session-1");
        assertThat(resaved.getValue().getContextDocumentIds()).containsExactly("doc-nm");
        return followed;
    }

    @Test
    void findFileThenExplainThisAssignmentReadsTheFoundFileInsteadOfBeingGloballyGated() {
        findFileThenAsk("explain this assignment");
    }

    @Test
    void findFileThenSummarizeItReadsTheFoundFile() {
        findFileThenAsk("summarize it");
    }

    @Test
    void findFileThenAskAboutAQuestionNumberReadsTheFoundFile() {
        findFileThenAsk("what is question 2?");
    }

    @Test
    void aSecondFollowUpAfterGenerationStaysOnTheFoundFileWhileResumingTheBedrockSession() {
        // The conversation now has BOTH a Bedrock session and context; "what is question 2?" matches nothing globally.
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(session("app-1", "bedrock-1", "doc-nm")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));
        preflightFindsNothing();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-1", "doc-nm", "Question 2 asks for ..."));

        AskResponse response = askService.ask(USER_ID, new AskRequest("what is question 2?", "app-1"));

        ArgumentCaptor<RetrieveAndGenerateRequest> request = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(request.capture());
        assertThat(request.getValue().sessionId()).isEqualTo("bedrock-1");
        RetrievalFilter filter = request.getValue().retrieveAndGenerateConfiguration().knowledgeBaseConfiguration()
                .retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.andAll().get(1).in().value().asList()).extracting(d -> d.asString()).containsExactly("doc-nm");
        assertThat(response.citations()).extracting(c -> c.documentId()).containsExactly("doc-nm");
        assertThat(response.sessionId()).isEqualTo("app-1");
    }

    @Test
    void anotherUsersSessionAndItsContextCanNeverBeReused() {
        // The lookup is partition-scoped to the authenticated user, so user A's session simply isn't there for B.
        when(askSessionRepository.findByUserAndSessionId("user-b", "sessionOfUserA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> askService.ask("user-b", new AskRequest("explain this assignment", "sessionOfUserA")))
                .isInstanceOf(AskSessionExpiredException.class);

        verify(retriever, never()).retrieveRelevant(any(), any(), anyInt());
        verify(documentRepository, never()).findByUserAndDocumentId(any(), any());
        verifyNoBedrockCall();
        verify(askSessionRepository, never()).save(any());
    }

    @Test
    void contextDocumentsThatAreNotThisUsersAreIgnoredSoRetrievalNeverWidensBeyondTheirOwnFiles() {
        // Defence in depth: even if a stored context id somehow isn't this user's document, it is dropped
        // and the turn falls back to ordinary relevance gating.
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(session("app-1", null, "someone-elses-doc")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "someone-elses-doc")).thenReturn(Optional.empty());
        preflightFindsNothing();

        AskResponse response = askService.ask(USER_ID, new AskRequest("explain this assignment", "app-1"));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
        verify(retriever).retrieveRelevant(any(RetrievalFilter.class), eq("explain this assignment"), anyInt());
        verifyNoBedrockCall();
    }

    @Test
    void anUnrelatedNewConversationStillGetsNormalRelevanceGating() {
        // No session at all: "explain this assignment" has nothing to be deictic about, and must be gated as before.
        preflightFindsNothing();

        AskResponse response = askService.ask(USER_ID, new AskRequest("explain this assignment", null));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
        assertThat(response.sessionId()).isNull();
        verify(retriever).retrieveRelevant(any(RetrievalFilter.class), eq("explain this assignment"), anyInt());
        verifyNoBedrockCall();
        verify(askSessionRepository, never()).save(any());
    }

    @Test
    void aNewFindQuestionInAContextConversationIsResolvedGloballyAndReplacesTheContext() {
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(session("app-1", null, "doc-nm")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-lease"))
                .thenReturn(Optional.of(sampleDocument("doc-lease", "Lease_Agreement.pdf")));
        preflightFinds("doc-lease");

        AskResponse response = askService.ask(USER_ID, new AskRequest("do I have a lease agreement?", "app-1"));

        verify(retriever).retrieveRelevant(any(RetrievalFilter.class), any(String.class), anyInt());
        verifyNoBedrockCall();
        assertThat(response.answer()).contains("Lease_Agreement.pdf");
        assertThat(response.sessionId()).isEqualTo("app-1");
        ArgumentCaptor<AskSession> saved = ArgumentCaptor.forClass(AskSession.class);
        verify(askSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getContextDocumentIds()).containsExactly("doc-lease");
    }

    @Test
    void aFindQuestionThatMatchesNothingIsNotSilentlyScopedToThePreviousFile() {
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(session("app-1", null, "doc-nm")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));
        preflightFindsNothing();

        AskResponse response = askService.ask(USER_ID, new AskRequest("do I have a passport scan?", "app-1"));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
        verifyNoBedrockCall();
        verify(askSessionRepository, never()).save(any());
    }

    @Test
    void aFindThatResolvesNothingUsableDoesNotCreateASession() {
        preflightFinds("doc-gone");
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-gone")).thenReturn(Optional.empty());

        AskResponse response = askService.ask(USER_ID, new AskRequest("do I have a lease agreement?", null));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.sessionId()).isNull();
        verify(askSessionRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- context-scoped structural retry

    private static RetrieveAndGenerateResponse refusalWithNoReferences() {
        return RetrieveAndGenerateResponse.builder()
                .sessionId("bedrock-refused")
                .output(RetrieveAndGenerateOutput.builder().text(NO_ANSWER).build())
                .build();
    }

    /** A LATER turn: the conversation already has a Bedrock session AND resolved context, and the question matches
     * nothing globally on its own - the state where the bounded recovery retry still applies. */
    private void laterTurnContextSession() {
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(session("app-1", "bedrock-1", "doc-nm")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));
        preflightFindsNothing();
    }

    private void contextSession() {
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(session("app-1", null, "doc-nm")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));
    }

    @Test
    void aContextScopedRefusalIsRetriedOnceWithTheAnchoredRequestAndRecovers() {
        // Live finding: RetrieveAndGenerate's own retrieval returned nothing for "explain this assignment" although a
        // scoped Retrieve with the same filter returns the file's chunks.
        laterTurnContextSession();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(refusalWithNoReferences())
                .thenReturn(responseWithCitation("bedrock-ok", "doc-nm", "The assignment covers numerical integration."));

        AskResponse response = askService.ask(USER_ID, new AskRequest("explain this assignment", "app-1"));

        ArgumentCaptor<RetrieveAndGenerateRequest> requests = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient, times(2)).retrieveAndGenerate(requests.capture());
        assertThat(requests.getAllValues().get(0).input().text()).isEqualTo("explain this assignment");
        assertThat(requests.getAllValues().get(1).input().text())
                .isEqualTo("Describe the contents of the file. Then: explain this assignment");
        for (RetrieveAndGenerateRequest request : requests.getAllValues()) {
            // Both attempts stay scoped to (this user AND the resolved file).
            RetrievalFilter filter = request.retrieveAndGenerateConfiguration().knowledgeBaseConfiguration()
                    .retrievalConfiguration().vectorSearchConfiguration().filter();
            assertThat(filter.andAll().get(0).equalsValue().value().asString()).isEqualTo(USER_ID);
            assertThat(filter.andAll().get(1).in().value().asList()).extracting(d -> d.asString()).containsExactly("doc-nm");
        }
        // The first attempt resumes the conversation; the recovery retry runs in a FRESH session.
        assertThat(requests.getAllValues().get(0).sessionId()).isEqualTo("bedrock-1");
        assertThat(requests.getAllValues().get(1).sessionId()).isNull();
        assertThat(response.answer()).contains("numerical integration");
        assertThat(response.citations()).extracting(c -> c.documentId()).containsExactly("doc-nm");

        // Only the successful attempt's Bedrock session is saved, into the same application session, context intact.
        ArgumentCaptor<AskSession> saved = ArgumentCaptor.forClass(AskSession.class);
        verify(askSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getBedrockSessionId()).isEqualTo("bedrock-ok");
        assertThat(saved.getValue().getApplicationSessionId()).isEqualTo("app-1");
        assertThat(saved.getValue().getContextDocumentIds()).containsExactly("doc-nm");
    }

    @Test
    void anAnswerWithNoRetrievedReferencesIsAlsoTreatedAsAFailedAttemptInAContextScopedTurn() {
        laterTurnContextSession();
        RetrieveAndGenerateResponse ungrounded = RetrieveAndGenerateResponse.builder()
                .sessionId("bedrock-x")
                .output(RetrieveAndGenerateOutput.builder().text("It probably involves some maths.").build())
                .build();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(ungrounded)
                .thenReturn(responseWithCitation("bedrock-ok", "doc-nm", "Grounded explanation."));

        AskResponse response = askService.ask(USER_ID, new AskRequest("tell me about this assignment", "app-1"));

        verify(bedrockAgentRuntimeClient, times(2)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
        assertThat(response.answer()).isEqualTo("Grounded explanation.");
    }

    @Test
    void theRetryHappensAtMostOnceAndAPersistentRefusalStaysAnHonestNoAnswerWithNoCitations() {
        laterTurnContextSession();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(refusalWithNoReferences());

        AskResponse response = askService.ask(USER_ID, new AskRequest("explain this assignment", "app-1"));

        verify(bedrockAgentRuntimeClient, times(2)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void aGroundedFirstAttemptIsNeverRetriedSoPreciseQuestionsKeepTheirOwnWording() {
        laterTurnContextSession();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-ok", "doc-nm", "Question 2 asks for the integral."));

        askService.ask(USER_ID, new AskRequest("what is question 2?", "app-1"));

        ArgumentCaptor<RetrieveAndGenerateRequest> requests = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient, times(1)).retrieveAndGenerate(requests.capture());
        assertThat(requests.getValue().input().text()).isEqualTo("what is question 2?");
        assertThat(requests.getValue().sessionId()).isEqualTo("bedrock-1");
    }

    @Test
    void aRefusalOutsideAContextScopedTurnIsNotRetried() {
        // The ordinary gated path already has a relevance-established document set; only the deictic path retries.
        preflightFinds("doc-1");
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("s1", "doc-1", NO_ANSWER));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));

        AskResponse response = askService.ask(USER_ID, new AskRequest("something the document does not say", null));

        verify(bedrockAgentRuntimeClient, times(1)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void aGatedRefusalThatRetrievedNothingIsRetriedOnceBecauseRetrievalFailedNotTheFile() {
        // "tell me about this assignment" scores high enough to pass the global gate, so it takes the gated path -
        // where RetrieveAndGenerate can still retrieve nothing and refuse (seen live).
        preflightFinds("doc-nm");
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(refusalWithNoReferences())
                .thenReturn(responseWithCitation("bedrock-ok", "doc-nm", "It is a numerical methods assignment."));

        AskResponse response = askService.ask(USER_ID, new AskRequest("tell me about this assignment", null));

        verify(bedrockAgentRuntimeClient, times(2)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
        assertThat(response.answer()).contains("numerical methods");
        assertThat(response.citations()).extracting(c -> c.documentId()).containsExactly("doc-nm");
    }

    @Test
    void aGatedNonRefusalAnsweredFromHistoryWithNoReferencesIsLeftAlone() {
        AskSession existing = session("app-1", "bedrock-1");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(existing));
        preflightFinds("doc-1");
        RetrieveAndGenerateResponse fromHistory = RetrieveAndGenerateResponse.builder()
                .sessionId("bedrock-1")
                .output(RetrieveAndGenerateOutput.builder().text("As mentioned earlier, the stipend is Rs 60,000.").build())
                .build();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class))).thenReturn(fromHistory);

        AskResponse response = askService.ask(USER_ID, new AskRequest("and the stipend?", "app-1"));

        verify(bedrockAgentRuntimeClient, times(1)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
        assertThat(response.answer()).contains("60,000");
    }

    @Test
    void aContextScopedAnswerThatCameBackWithoutReferenceObjectsStillCitesTheScopedFile() {
        // Seen live: a good answer whose response carried no retrievedReferences. Retrieval was restricted to the
        // resolved file, so the file is the honest source.
        contextSession();
        RetrieveAndGenerateResponse noRefs = RetrieveAndGenerateResponse.builder()
                .sessionId("bedrock-ok")
                .output(RetrieveAndGenerateOutput.builder().text("The file contains plots of a Gaussian function.").build())
                .build();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class))).thenReturn(noRefs);

        AskResponse response = askService.ask(USER_ID, new AskRequest("explain this assignment", "app-1"));

        assertThat(response.answer()).contains("Gaussian");
        assertThat(response.citations()).extracting(c -> c.fileName()).containsExactly("NumericalMethods_Assignment1.pdf");
        // A source FILE only: no chunk snippet or media timestamp is invented, since Bedrock returned no reference.
        assertThat(response.citations().get(0).snippet()).isEmpty();
        assertThat(response.citations().get(0).mediaTimestamp()).isNull();
    }

    @Test
    void aContextScopedRefusalNeverGetsTheContextFileAttachedAsASource() {
        contextSession();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(refusalWithNoReferences());

        AskResponse response = askService.ask(USER_ID, new AskRequest("explain this assignment", "app-1"));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void anOrdinaryGatedAnswerWithoutReferencesDoesNotInventSources() {
        preflightFinds("doc-1");
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));
        RetrieveAndGenerateResponse noRefs = RetrieveAndGenerateResponse.builder()
                .sessionId("s1")
                .output(RetrieveAndGenerateOutput.builder().text("Some answer without references.").build())
                .build();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class))).thenReturn(noRefs);

        AskResponse response = askService.ask(USER_ID, new AskRequest("a question", null));

        assertThat(response.citations()).isEmpty();
    }

    @Test
    void bedrocksCannedFailureMessageIsTreatedAsARefusalAndReplacedByOurDeterministicNoAnswer() {
        preflightFinds("doc-1");
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1")).thenReturn(Optional.of(sampleDocument("doc-1")));
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("s1", "doc-1", "Sorry, I am unable to assist you with this request."));

        AskResponse response = askService.ask(USER_ID, new AskRequest("a question about the offer", null));

        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void theRetryRunsInAFreshBedrockSessionEvenWhenTheConversationHadOne() {
        // Turn N of a long conversation: Bedrock session + context; the first attempt fails, the retry must not reuse it.
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-1")).thenReturn(Optional.of(session("app-1", "bedrock-long", "doc-nm")));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-nm"))
                .thenReturn(Optional.of(sampleDocument("doc-nm", "NumericalMethods_Assignment1.pdf")));
        preflightFindsNothing();
        RetrieveAndGenerateResponse canned = RetrieveAndGenerateResponse.builder()
                .sessionId("bedrock-long")
                .output(RetrieveAndGenerateOutput.builder().text("Sorry, I am unable to assist you with this request.").build())
                .build();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(canned)
                .thenReturn(responseWithCitation("bedrock-fresh", "doc-nm", "It is a numerical methods assignment."));

        AskResponse response = askService.ask(USER_ID, new AskRequest("tell me about this assignment", "app-1"));

        ArgumentCaptor<RetrieveAndGenerateRequest> requests = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient, times(2)).retrieveAndGenerate(requests.capture());
        assertThat(requests.getAllValues().get(0).sessionId()).isEqualTo("bedrock-long");
        assertThat(requests.getAllValues().get(1).sessionId()).isNull();
        assertThat(response.answer()).contains("numerical methods");
        ArgumentCaptor<AskSession> saved = ArgumentCaptor.forClass(AskSession.class);
        verify(askSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getBedrockSessionId()).isEqualTo("bedrock-fresh");
        assertThat(saved.getValue().getContextDocumentIds()).containsExactly("doc-nm");
    }

    @Test
    void theFirstPostFindContextualTurnCostsExactlyOneGenerationCallWithTheAnchoredWording() {
        // contextDocumentIds non-empty + bedrockSessionId == null: a state-based rule, not a phrase list.
        contextSession();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-ok", "doc-nm", "The assignment covers numerical integration."));

        AskResponse response = askService.ask(USER_ID, new AskRequest("some wording nobody special-cased", "app-1"));

        ArgumentCaptor<RetrieveAndGenerateRequest> requests = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient, times(1)).retrieveAndGenerate(requests.capture());
        assertThat(requests.getValue().input().text())
                .isEqualTo("Describe the contents of the file. Then: some wording nobody special-cased");
        assertThat(requests.getValue().sessionId()).isNull();
        // Still scoped to (authenticated user AND the resolved file), and never globally gated.
        RetrievalFilter filter = requests.getValue().retrieveAndGenerateConfiguration().knowledgeBaseConfiguration()
                .retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.andAll().get(0).equalsValue().value().asString()).isEqualTo(USER_ID);
        assertThat(filter.andAll().get(1).in().value().asList()).extracting(d -> d.asString()).containsExactly("doc-nm");
        verify(retriever, never()).retrieveRelevant(any(), any(), anyInt());
        assertThat(response.citations()).extracting(c -> c.documentId()).containsExactly("doc-nm");

        ArgumentCaptor<AskSession> saved = ArgumentCaptor.forClass(AskSession.class);
        verify(askSessionRepository).save(saved.capture());
        assertThat(saved.getValue().getBedrockSessionId()).isEqualTo("bedrock-ok");
        assertThat(saved.getValue().getContextDocumentIds()).containsExactly("doc-nm");
    }

    @Test
    void aFirstPostFindTurnThatRefusesIsNotRetriedBecauseItAlreadyUsedTheRecoveryWordingInAFreshSession() {
        contextSession();
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(refusalWithNoReferences());

        AskResponse response = askService.ask(USER_ID, new AskRequest("explain this assignment", "app-1"));

        verify(bedrockAgentRuntimeClient, times(1)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();
    }

    private void verifyNoBedrockCall() {
        verify(bedrockAgentRuntimeClient, never()).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    private static AskSession argThatSessionMapsBedrockId(String expectedBedrockSessionId) {
        return org.mockito.ArgumentMatchers.argThat(session -> expectedBedrockSessionId.equals(session.getBedrockSessionId()));
    }
}
