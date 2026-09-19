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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.Citation;
import software.amazon.awssdk.services.bedrockagentruntime.model.FilterAttribute;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AskServiceTest {

    private static final String USER_ID = "user-abc";

    @Mock
    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AskSessionRepository askSessionRepository;

    private AskService askService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        askService = new AskService(bedrockAgentRuntimeClient, documentRepository, askSessionRepository);
    }

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

    private static RetrieveAndGenerateResponse responseWithCitation(String sessionId, String documentId) {
        Citation citation = Citation.builder()
                .retrievedReferences(RetrievedReference.builder()
                        .content(RetrievalResultContent.builder().text("relevant excerpt").build())
                        .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                        .build())
                .build();
        return RetrieveAndGenerateResponse.builder()
                .sessionId(sessionId)
                .output(RetrieveAndGenerateOutput.builder().text("the generated answer").build())
                .citations(citation)
                .build();
    }

    @Test
    void rejectsABlankQuestion() {
        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("  ", null)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoBedrockCall();
    }

    @Test
    void firstTurnCreatesANewApplicationSessionAndNeverSendsABedrockSessionId() {
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-1"));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-1"))
                .thenReturn(Optional.of(sampleDocument("doc-1")));

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

        // The raw Bedrock session ID must never reach the response — only our own opaque one.
        verify(askSessionRepository).save(argThatSessionMapsBedrockId("bedrock-session-xyz"));
    }

    @Test
    void followUpResolvesTheBedrockSessionUnderTheAuthenticatedUsersPartitionAndReusesTheSameApplicationSessionId() {
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("bedrock-session-xyz");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
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
    void aSessionIdNotFoundUnderThisUsersPartitionExpiresWithoutEverCallingBedrock() {
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "someone-elses-or-expired-session"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("continue?", "someone-elses-or-expired-session")))
                .isInstanceOf(AskSessionExpiredException.class);

        verifyNoBedrockCall();
    }

    @Test
    void bedrockRejectingAnExistingSessionAsInvalidExpiresTheMappingRatherThanRetryingTransparently() {
        // Exact wording confirmed live during the Phase 6 spike for an invalid/expired session.
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("stale-bedrock-session");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(ValidationException.builder()
                        .message("Session with Id stale-bedrock-session is not valid. Please check and try again.")
                        .build());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("continue?", "app-session-1")))
                .isInstanceOf(AskSessionExpiredException.class);

        verify(askSessionRepository).delete(USER_ID, "app-session-1");
        // Only ever the one call — no transparent contextual retry without history.
        verify(bedrockAgentRuntimeClient, times(1)).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    @Test
    void anUnrelatedValidationExceptionOnAnExistingSessionDoesNotExpireOrDeleteTheSession() {
        // Regression test (post-approval correction): a ValidationException that has nothing
        // to do with session validity — e.g. malformed/unsupported input — must not destroy an
        // otherwise-healthy session mapping, unlike the invalid/expired-session case above.
        AskSession existing = new AskSession();
        existing.setBedrockSessionId("healthy-bedrock-session");
        when(askSessionRepository.findByUserAndSessionId(USER_ID, "app-session-1")).thenReturn(Optional.of(existing));
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(ValidationException.builder()
                        .message("The text field must not exceed 1000 characters.")
                        .build());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("continue?", "app-session-1")))
                .isInstanceOf(InvalidRequestException.class);

        verify(askSessionRepository, never()).delete(any(), any());
    }

    @Test
    void throttlingIsRetryable() {
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(ThrottlingException.builder().message("busy").build());

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("question", null)))
                .isInstanceOf(RetrievalUnavailableException.class)
                .satisfies(e -> assertThat(((RetrievalUnavailableException) e).isRetryable()).isTrue());
    }

    @Test
    void otherUpstreamFailuresAreNotRetryable() {
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenThrow(software.amazon.awssdk.core.exception.SdkClientException.create("boom"));

        assertThatThrownBy(() -> askService.ask(USER_ID, new AskRequest("question", null)))
                .isInstanceOf(RetrievalUnavailableException.class)
                .satisfies(e -> assertThat(((RetrievalUnavailableException) e).isRetryable()).isFalse());
    }

    @Test
    void skipsACitationWhoseDocumentCannotBeResolvedForThisUser() {
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-owned-by-someone-else"));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "doc-owned-by-someone-else")).thenReturn(Optional.empty());

        AskResponse response = askService.ask(USER_ID, new AskRequest("question", null));

        assertThat(response.citations()).isEmpty();
    }

    @Test
    void everyCallInjectsTheAuthenticatedUsersIdAsTheOnlyTenantFilterRegardlessOfRequestBody() {
        when(bedrockAgentRuntimeClient.retrieveAndGenerate(any(RetrieveAndGenerateRequest.class)))
                .thenReturn(responseWithCitation("bedrock-session-xyz", "doc-1"));
        when(documentRepository.findByUserAndDocumentId(eq(USER_ID), any())).thenReturn(Optional.of(sampleDocument("doc-1")));

        askService.ask(USER_ID, new AskRequest("question", null));

        ArgumentCaptor<RetrieveAndGenerateRequest> captor = ArgumentCaptor.forClass(RetrieveAndGenerateRequest.class);
        verify(bedrockAgentRuntimeClient).retrieveAndGenerate(captor.capture());
        RetrievalFilter filter = captor.getValue().retrieveAndGenerateConfiguration()
                .knowledgeBaseConfiguration().retrievalConfiguration().vectorSearchConfiguration().filter();
        FilterAttribute equalsValue = filter.equalsValue();
        assertThat(equalsValue.key()).isEqualTo("userId");
        assertThat(equalsValue.value().asString()).isEqualTo(USER_ID);
    }

    private void verifyNoBedrockCall() {
        verify(bedrockAgentRuntimeClient, never()).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    private static AskSession argThatSessionMapsBedrockId(String expectedBedrockSessionId) {
        return org.mockito.ArgumentMatchers.argThat(session -> expectedBedrockSessionId.equals(session.getBedrockSessionId()));
    }
}
