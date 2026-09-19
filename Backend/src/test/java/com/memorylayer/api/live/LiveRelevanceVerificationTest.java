package com.memorylayer.api.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylayer.api.ask.AskService;
import com.memorylayer.api.ask.AskSession;
import com.memorylayer.api.ask.AskSessionRepository;
import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.dto.AskRequest;
import com.memorylayer.api.dto.AskResponse;
import com.memorylayer.api.dto.SearchRequest;
import com.memorylayer.api.dto.SearchResponse;
import com.memorylayer.api.search.KnowledgeBaseRetriever;
import com.memorylayer.api.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Manual, opt-in verification against the <b>real</b> Knowledge Base (read-only Bedrock calls; no
 * DynamoDB access - documents/sessions are stubbed). Skipped in normal builds.
 *
 * <pre>
 *   LIVE_KB=1 KNOWLEDGE_BASE_ID=... ASK_MODEL_ARN=... \
 *   LIVE_USER_A=&lt;sub owning the Tata JPEG&gt; LIVE_USER_B=&lt;sub owning NumericalMethods_Assignment1.pdf&gt; \
 *   LIVE_DOCS_JSON=&lt;path to a `dynamodb scan` of documentId+fileName&gt; \
 *   mvn test -Dtest=LiveRelevanceVerificationTest
 * </pre>
 * Prints answers and filenames only - never scores.
 */
@EnabledIfEnvironmentVariable(named = "LIVE_KB", matches = "1")
class LiveRelevanceVerificationTest {

    private static final String NO_ANSWER = "I couldn't find anything in your memories that answers that.";

    private String userA;
    private String userB;
    private BedrockAgentRuntimeClient client;
    private SearchService searchService;
    private AskService askService;
    private final Map<String, AskSession> sessions = new HashMap<>();

    // A fresh spy per test, so "generation was never called" assertions can't be polluted by other tests.
    @BeforeEach
    void wire() throws Exception {
        userA = System.getenv("LIVE_USER_A");
        userB = System.getenv("LIVE_USER_B");
        client = spy(BedrockAgentRuntimeClient.builder().region(Region.AP_SOUTH_1)
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build());
        KnowledgeBaseRetriever retriever = new KnowledgeBaseRetriever(client);

        Map<String, String> names = new HashMap<>();
        JsonNode items = new ObjectMapper().readTree(Files.readString(Path.of(System.getenv("LIVE_DOCS_JSON")))).get("Items");
        for (JsonNode item : items) {
            if (item.has("fileName")) {
                names.put(item.get("SK").get("S").asText().substring(item.get("SK").get("S").asText().indexOf('#') + 1),
                        item.get("fileName").get("S").asText());
            }
        }
        DocumentRepository documents = mock(DocumentRepository.class);
        when(documents.findByUserAndDocumentId(anyString(), anyString())).thenAnswer(invocation -> {
            String documentId = invocation.getArgument(1);
            String fileName = names.get(documentId);
            if (fileName == null) {
                return Optional.empty();
            }
            Document document = new Document();
            document.setDocumentId(documentId);
            document.setUserId(invocation.getArgument(0));
            document.setFileName(fileName);
            document.setMediaCategory(fileName.endsWith(".pdf") ? MediaCategory.DOCUMENT : MediaCategory.IMAGE);
            document.setMimeType(fileName.endsWith(".pdf") ? "application/pdf" : "image/jpeg");
            document.setStatus(DocumentStatus.READY);
            return Optional.of(document);
        });

        AskSessionRepository sessionRepository = mock(AskSessionRepository.class);
        when(sessionRepository.findByUserAndSessionId(anyString(), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(sessions.get(invocation.getArgument(1))));
        org.mockito.Mockito.doAnswer(invocation -> {
            AskSession session = invocation.getArgument(0);
            sessions.put(session.getApplicationSessionId(), session);
            return null;
        }).when(sessionRepository).save(any(AskSession.class));

        searchService = new SearchService(retriever, documents);
        askService = new AskService(client, retriever, documents, sessionRepository);
    }

    private static void show(String label, AskResponse response) {
        System.out.println("LIVE| " + label + "\n      answer   : " + response.answer()
                + "\n      citations: " + response.citations().stream().map(c -> c.fileName()).toList());
    }

    private static void show(String label, SearchResponse response) {
        System.out.println("LIVE| " + label + "\n      results  : "
                + response.results().stream().map(r -> r.document().fileName()).toList());
    }

    @Test
    void searchAbsentQueriesReturnNothing() {
        for (String user : new String[]{userA, userB}) {
            for (String query : new String[]{"my electricity bill", "recipe for chocolate chip cookies",
                    "flight booking confirmation to Mumbai", "quantum chromodynamics lagrangian renormalization"}) {
                SearchResponse response = searchService.search(user, new SearchRequest(query, null, null));
                show("SEARCH absent  " + query, response);
                assertThat(response.results()).as(query).isEmpty();
            }
        }
    }

    @Test
    void searchRelevantQueriesReturnTheRightFile() {
        SearchResponse numerical = searchService.search(userB, new SearchRequest("numerical methods assignment", null, null));
        show("SEARCH relevant numerical methods assignment", numerical);
        assertThat(numerical.results()).extracting(r -> r.document().fileName()).contains("NumericalMethods_Assignment1.pdf");

        SearchResponse tata = searchService.search(userA, new SearchRequest("Tata Motors corporate entrepreneurship", null, null));
        show("SEARCH relevant Tata Motors corporate entrepreneurship", tata);
        assertThat(tata.results()).extracting(r -> r.document().fileName()).contains("photo_6106897192711820494_y.jpg");
        assertThat(tata.results()).hasSize(1);
    }

    @Test
    void askWithNoMatchingMemoryIsAGroundedNoAnswerWithZeroCitationsAndNoGeneration() {
        for (String user : new String[]{userA, userB}) {
            AskResponse response = askService.ask(user, new AskRequest("How much AWS credit did I have?", null));
            show("ASK absent  How much AWS credit did I have?", response);
            assertThat(response.answer()).isEqualTo(NO_ANSWER);
            assertThat(response.citations()).isEmpty();
        }
        verify(client, never()).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    @Test
    void askExistenceQuestionRecognisesTheActualFile() {
        AskResponse response = askService.ask(userB, new AskRequest("do I have numerical methods assignment?", null));
        show("ASK find  do I have numerical methods assignment?", response);
        assertThat(response.answer()).contains("NumericalMethods_Assignment1.pdf").doesNotContain("couldn't find");
        assertThat(response.citations()).extracting(c -> c.fileName()).containsExactly("NumericalMethods_Assignment1.pdf");
    }

    @Test
    void askKnownContentQuestionsAreGroundedWithOnlyRelevantSources() {
        AskResponse tata = askService.ask(userA, new AskRequest("What does the note say Tata Motors can do to renew innovation?", null));
        show("ASK content  Tata Motors renew innovation", tata);
        assertThat(tata.answer()).isNotEqualTo(NO_ANSWER);
        assertThat(tata.citations()).extracting(c -> c.fileName()).containsOnly("photo_6106897192711820494_y.jpg");

        AskResponse numerical = askService.ask(userB, new AskRequest("What does the assignment say about integration limits and sigma?", null));
        show("ASK content  assignment integration limits and sigma", numerical);
        assertThat(numerical.answer()).isNotEqualTo(NO_ANSWER);
        assertThat(numerical.citations()).extracting(c -> c.fileName()).containsOnly("NumericalMethods_Assignment1.pdf");
    }

    @Test
    void crossUserIsolationHolds() {
        // User B has no Tata document; the same question must find nothing for them.
        AskResponse response = askService.ask(userB, new AskRequest("What does the note say Tata Motors can do to renew innovation?", null));
        show("ASK cross-user  (B asks A's Tata question)", response);
        assertThat(response.answer()).isEqualTo(NO_ANSWER);
        assertThat(response.citations()).isEmpty();

        SearchResponse search = searchService.search(userB, new SearchRequest("Tata Motors corporate entrepreneurship", null, null));
        show("SEARCH cross-user  (B searches A's Tata topic)", search);
        assertThat(search.results()).isEmpty();
        verify(client, never()).retrieveAndGenerate(any(RetrieveAndGenerateRequest.class));
    }

    @Test
    void aFollowUpWithNoStandaloneMatchStillWorksThroughTheSession() {
        AskResponse first = askService.ask(userA, new AskRequest("What does the note say Tata Motors can do to renew innovation?", null));
        assertThat(first.sessionId()).isNotBlank();
        AskResponse second = askService.ask(userA, new AskRequest("Who resists these entrepreneurial teams?", first.sessionId()));
        show("ASK follow-up  Who resists these entrepreneurial teams?", second);
        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.answer()).isNotBlank();
    }

    @Test
    void findThenExplainAndSummarizeThisAssignmentReadTheFoundFile() {
        AskResponse found = askService.ask(userB, new AskRequest("do I have a numerical methods assignment?", null));
        show("ASK find  do I have a numerical methods assignment?", found);
        assertThat(found.citations()).extracting(c -> c.fileName()).containsExactly("NumericalMethods_Assignment1.pdf");
        assertThat(found.sessionId()).isNotBlank();

        AskResponse explained = askService.ask(userB, new AskRequest("explain this assignment", found.sessionId()));
        show("ASK follow-up  explain this assignment", explained);
        assertThat(explained.answer()).isNotEqualTo(NO_ANSWER);
        assertThat(explained.citations()).extracting(c -> c.fileName()).containsOnly("NumericalMethods_Assignment1.pdf");
        assertThat(explained.sessionId()).isEqualTo(found.sessionId());

        AskResponse summarized = askService.ask(userB, new AskRequest("summarize it", found.sessionId()));
        show("ASK follow-up  summarize it", summarized);
        assertThat(summarized.answer()).isNotEqualTo(NO_ANSWER);
        assertThat(summarized.citations()).extracting(c -> c.fileName()).containsOnly("NumericalMethods_Assignment1.pdf");

        // Other wordings of the same deictic request, including ones Bedrock's own retrieval mishandled unaided.
        for (String wording : new String[]{"what is question 2?", "tell me about this assignment", "please explain this assignment"}) {
            AskResponse other = askService.ask(userB, new AskRequest(wording, found.sessionId()));
            show("ASK follow-up  " + wording, other);
            assertThat(other.answer()).as(wording).isNotEqualTo(NO_ANSWER);
            assertThat(other.citations()).as(wording).extracting(c -> c.fileName()).containsOnly("NumericalMethods_Assignment1.pdf");
        }

        // A brand-new conversation borrows no context: for an account with no assignment, the same words are simply
        // relevance-gated to a no-answer (for user B they can legitimately match the file on their own).
        AskResponse fresh = askService.ask(userA, new AskRequest("explain this assignment", null));
        show("ASK new conversation (account with no assignment)  explain this assignment", fresh);
        assertThat(fresh.answer()).isEqualTo(NO_ANSWER);
        assertThat(fresh.citations()).isEmpty();
    }
}
