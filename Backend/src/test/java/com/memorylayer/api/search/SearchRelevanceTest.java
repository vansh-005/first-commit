package com.memorylayer.api.search;

import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.document.DocumentStatus;
import com.memorylayer.api.document.MediaCategory;
import com.memorylayer.api.dto.SearchRequest;
import com.memorylayer.api.dto.SearchResponse;
import com.memorylayer.api.error.RetrievalUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.ThrottlingException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Positive/negative regression tests for the relevance gate as /search applies it (real
 * {@link KnowledgeBaseRetriever} + {@link SearchService}, only Bedrock and DynamoDB mocked). */
class SearchRelevanceTest {

    private static final String USER_ID = "user-abc";

    @Mock
    private BedrockAgentRuntimeClient client;

    @Mock
    private DocumentRepository documentRepository;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        KnowledgeBaseRetriever retriever = new KnowledgeBaseRetriever(client, "kb-1", KnowledgeBaseRetriever.DEFAULT_MIN_RELEVANCE_SCORE);
        searchService = new SearchService(retriever, documentRepository);
    }

    private static KnowledgeBaseRetrievalResult chunk(String documentId, double score) {
        return KnowledgeBaseRetrievalResult.builder()
                .content(c -> c.text("chunk of " + documentId))
                .metadata(Map.of("documentId", software.amazon.awssdk.core.document.Document.fromString(documentId)))
                .score(score)
                .build();
    }

    private static Document document(String documentId, String fileName) {
        Document document = new Document();
        document.setDocumentId(documentId);
        document.setUserId(USER_ID);
        document.setFileName(fileName);
        document.setMediaCategory(MediaCategory.DOCUMENT);
        document.setMimeType("application/pdf");
        document.setStatus(DocumentStatus.READY);
        return document;
    }

    private void retrievalReturns(KnowledgeBaseRetrievalResult... chunks) {
        when(client.retrieve(any(RetrieveRequest.class))).thenReturn(RetrieveResponse.builder().retrievalResults(chunks).build());
    }

    @Test
    void anAbsentQueryReturnsAnEmptyResultListNotArbitraryNearestNeighbours() {
        // "my electricity bill": Bedrock still returns its k nearest chunks, best 0.5566.
        retrievalReturns(chunk("numerical-methods", 0.5566), chunk("numerical-methods", 0.5380), chunk("photo", 0.5229));

        SearchResponse response = searchService.search(USER_ID, new SearchRequest("my electricity bill", null, null));

        assertThat(response.results()).isEmpty();
        verify(documentRepository, never()).findByUserAndDocumentId(anyString(), anyString());
    }

    @Test
    void aRelevantQueryReturnsTheMatchingFile() {
        // "numerical methods assignment": best chunk 0.7853 (measured live).
        retrievalReturns(chunk("nm", 0.7853), chunk("nm", 0.7452), chunk("other", 0.5197));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "nm"))
                .thenReturn(Optional.of(document("nm", "NumericalMethods_Assignment1.pdf")));

        SearchResponse response = searchService.search(USER_ID, new SearchRequest("numerical methods assignment", null, null));

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).document().fileName()).isEqualTo("NumericalMethods_Assignment1.pdf");
        // The low-scoring "other" document never even reached the lookup.
        verify(documentRepository, never()).findByUserAndDocumentId(USER_ID, "other");
    }

    @Test
    void theGateRunsBeforeDedupSoALowChunkOfAnOtherwiseAbsentDocumentCannotSneakIn() {
        // Tata query: the JPEG scores 0.8071; the unrelated markdown guide's chunks are all ~0.54.
        retrievalReturns(chunk("tata", 0.8071), chunk("guide", 0.5547), chunk("guide", 0.5415), chunk("guide", 0.5396));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "tata")).thenReturn(Optional.of(document("tata", "photo.jpg")));

        SearchResponse response = searchService.search(USER_ID, new SearchRequest("Tata Motors corporate entrepreneurship", null, null));

        assertThat(response.results()).extracting(r -> r.document().documentId()).containsExactly("tata");
    }

    @Test
    void theThresholdIsInclusive() {
        retrievalReturns(chunk("edge", KnowledgeBaseRetriever.DEFAULT_MIN_RELEVANCE_SCORE));
        when(documentRepository.findByUserAndDocumentId(USER_ID, "edge")).thenReturn(Optional.of(document("edge", "edge.pdf")));

        assertThat(searchService.search(USER_ID, new SearchRequest("q", null, null)).results()).hasSize(1);
    }

    @Test
    void tenantIsolationIsUnchangedTheUserFilterIsStillInjectedServerSide() {
        retrievalReturns();

        searchService.search(USER_ID, new SearchRequest("anything", null, null));

        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(client).retrieve(captor.capture());
        var filter = captor.getValue().retrievalConfiguration().vectorSearchConfiguration().filter();
        assertThat(filter.equalsValue().key()).isEqualTo("userId");
        assertThat(filter.equalsValue().value().asString()).isEqualTo(USER_ID);
    }

    @Test
    void throttlingStillMapsToARetryableUnavailableError() {
        when(client.retrieve(any(RetrieveRequest.class))).thenThrow(ThrottlingException.builder().message("busy").build());

        assertThatThrownBy(() -> searchService.search(USER_ID, new SearchRequest("q", null, null)))
                .isInstanceOf(RetrievalUnavailableException.class)
                .satisfies(e -> assertThat(((RetrievalUnavailableException) e).isRetryable()).isTrue());
    }

    @Test
    void theThresholdOverrideIsParsedDefensively() {
        assertThat(KnowledgeBaseRetriever.resolveMinScore(null)).isEqualTo(0.62);
        assertThat(KnowledgeBaseRetriever.resolveMinScore(" ")).isEqualTo(0.62);
        assertThat(KnowledgeBaseRetriever.resolveMinScore("0.7")).isEqualTo(0.7);
        assertThat(KnowledgeBaseRetriever.resolveMinScore("abc")).isEqualTo(0.62);
        assertThat(KnowledgeBaseRetriever.resolveMinScore("1.5")).isEqualTo(0.62);
        assertThat(KnowledgeBaseRetriever.resolveMinScore("-0.1")).isEqualTo(0.62);
    }
}
