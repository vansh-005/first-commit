package com.memorylayer.api.search;

import com.memorylayer.api.error.RetrievalUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.ThrottlingException;

import java.util.List;

/**
 * The one place {@code Retrieve} is called for the whole backend: {@code /search} uses it directly
 * and {@code /ask} uses it as its relevance preflight, so both apply the <b>same</b> gate to the
 * <b>same</b> tenant-filtered retrieval. The caller supplies the {@link RetrievalFilter} (always
 * built from the authenticated {@code userId}, see {@link RetrievalFilters}).
 *
 * <h3>MIN_RELEVANCE_SCORE = 0.62</h3>
 * Measured against the real Knowledge Base (Titan Text Embeddings V2 + S3 Vectors, cosine) with
 * labelled queries across three accounts:
 * <ul>
 *   <li>Clearly absent topics (45+ queries: bills, flights, recipes, "boiling point of mercury on
 *       Jupiter", …): best chunk per query topped out at <b>0.5955</b>.</li>
 *   <li>Clearly relevant: 0.74–0.85 (e.g. "numerical methods assignment" 0.785, "Tata Motors
 *       corporate entrepreneurship" 0.807). Natural paraphrases of relevant content: 0.64–0.73.</li>
 *   <li>Vague topical queries against relevant files (e.g. "Newton Raphson method") sit at
 *       0.60–0.62 and are deliberately treated as not confident enough.</li>
 * </ul>
 * 0.62 leaves ~0.025 headroom above the noise ceiling while keeping every natural query tested.
 * It can be tuned without a code change through the {@code MIN_RELEVANCE_SCORE} environment
 * variable. Scores are never logged.
 */
@Service
public class KnowledgeBaseRetriever {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseRetriever.class);

    public static final double DEFAULT_MIN_RELEVANCE_SCORE = 0.62;

    private final BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    private final String knowledgeBaseId;
    private final double minRelevanceScore;

    @Autowired
    public KnowledgeBaseRetriever(BedrockAgentRuntimeClient bedrockAgentRuntimeClient) {
        this(bedrockAgentRuntimeClient, System.getenv("KNOWLEDGE_BASE_ID"),
                resolveMinScore(System.getenv("MIN_RELEVANCE_SCORE")));
    }

    public KnowledgeBaseRetriever(BedrockAgentRuntimeClient bedrockAgentRuntimeClient, String knowledgeBaseId,
                                  double minRelevanceScore) {
        this.bedrockAgentRuntimeClient = bedrockAgentRuntimeClient;
        this.knowledgeBaseId = knowledgeBaseId;
        this.minRelevanceScore = minRelevanceScore;
    }

    /** Retrieves up to {@code numberOfResults} chunks under {@code filter}, keeping only those that
     * clear the relevance gate. Returns an empty list — never nearest-neighbour noise — when nothing
     * is relevant. */
    public List<KnowledgeBaseRetrievalResult> retrieveRelevant(RetrievalFilter filter, String query, int numberOfResults) {
        RetrieveRequest request = RetrieveRequest.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .retrievalQuery(KnowledgeBaseQuery.builder().text(query).build())
                .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                        .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                .numberOfResults(numberOfResults)
                                .filter(filter)
                                .build())
                        .build())
                .build();

        RetrieveResponse response;
        try {
            response = bedrockAgentRuntimeClient.retrieve(request);
        } catch (ThrottlingException e) {
            log.warn("Bedrock Retrieve throttled", e);
            throw new RetrievalUnavailableException("The service is temporarily busy. Please retry shortly.", true);
        } catch (SdkException e) {
            log.error("Bedrock Retrieve failed", e);
            throw new RetrievalUnavailableException("Search is temporarily unavailable. Please try again.", false);
        }

        List<KnowledgeBaseRetrievalResult> relevant = RelevanceGate.filter(response.retrievalResults(), minRelevanceScore);
        // Counts only — scores must never reach the logs.
        log.debug("relevance gate kept {} of {} chunks", relevant.size(), response.retrievalResults().size());
        return relevant;
    }

    /** Parses the optional override; anything missing/invalid/out of range falls back to the measured default. */
    static double resolveMinScore(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MIN_RELEVANCE_SCORE;
        }
        try {
            double parsed = Double.parseDouble(raw.trim());
            if (parsed >= 0.0 && parsed <= 1.0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // fall through to the default
        }
        log.warn("Ignoring invalid MIN_RELEVANCE_SCORE override; using default {}", DEFAULT_MIN_RELEVANCE_SCORE);
        return DEFAULT_MIN_RELEVANCE_SCORE;
    }
}
