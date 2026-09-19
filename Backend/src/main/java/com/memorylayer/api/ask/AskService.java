package com.memorylayer.api.ask;

import com.memorylayer.api.document.Document;
import com.memorylayer.api.document.DocumentKeys;
import com.memorylayer.api.document.DocumentRepository;
import com.memorylayer.api.dto.AskRequest;
import com.memorylayer.api.dto.AskResponse;
import com.memorylayer.api.dto.Citation;
import com.memorylayer.api.error.AskSessionExpiredException;
import com.memorylayer.api.error.InvalidRequestException;
import com.memorylayer.api.error.RetrievalUnavailableException;
import com.memorylayer.api.dto.MediaTimestamp;
import com.memorylayer.api.search.KnowledgeBaseRetriever;
import com.memorylayer.api.search.RetrievalContentMapper;
import com.memorylayer.api.search.RetrievalFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.GenerationConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.PromptTemplate;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalFilter;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateInput;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveAndGenerateType;
import software.amazon.awssdk.services.bedrockagentruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockagentruntime.model.ValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Docs/API.md §20. Uses Bedrock Knowledge Base {@code RetrieveAndGenerate} — grounded Q&A,
 * never plain generation. Every call injects {@code userId == authenticatedUserId} server-side
 * via {@link RetrievalFilters}, the same tenant-isolation filter {@code /search} uses.
 *
 * <p><b>Pipeline (Phase 8 correctness fix):</b> Ask no longer trusts {@code RetrieveAndGenerate}
 * alone to decide whether useful context exists — it always attaches whatever it retrieved, even to
 * a refusal. Every question first runs a preflight {@code Retrieve} through
 * {@link KnowledgeBaseRetriever} (same tenant filter, same relevance gate as {@code /search}).
 * <ol>
 *   <li>Nothing relevant (first turn) &rarr; a deterministic no-answer, zero citations, and
 *       {@code RetrieveAndGenerate} is <b>not called</b>.</li>
 *   <li>A "do I have ...?" / "find my ..." question &rarr; answered from the relevant documents'
 *       metadata ({@link FindIntent}); the model can't see filenames.</li>
 *   <li>Otherwise {@code RetrieveAndGenerate} runs with {@link AskPrompt#TEMPLATE}, restricted to
 *       the relevant documents, and only citations from those documents are returned.</li>
 * </ol>
 * Follow-up turns (an existing session) are the one exception to the gate: a follow-up such as
 * "and the stipend?" only makes sense with conversation history, so it scores low standalone.
 * When the gate finds nothing for a follow-up, generation still runs (tenant filter only) and a
 * refusal has its citations dropped.
 *
 * <p><b>Session handling (Phase 6 amendment):</b> the frontend never sees or supplies a raw
 * Bedrock session ID. It only ever holds an opaque, application-issued {@code sessionId},
 * resolved to the underlying Bedrock session through {@link AskSessionRepository} — a lookup
 * scoped to the authenticated user's own DynamoDB partition. This binds every conversation to
 * the Cognito identity that started it and makes cross-user Bedrock-session reuse structurally
 * impossible, the same way {@link DocumentRepository} already binds document ownership. No
 * conversation text/history is ever persisted — only the pointer to Bedrock's own session
 * state, with a TTL as a cleanup safety net independent of Bedrock's own (undocumented)
 * session lifetime.
 *
 * <p>If Bedrock rejects an existing session as invalid/expired, this does <b>not</b>
 * transparently retry the same contextual follow-up without its history — that would silently
 * answer as if the conversation continued when it didn't. Instead the mapping is deleted and
 * {@link AskSessionExpiredException} propagates as a specific, client-visible signal to start
 * a new conversation. Only the specific invalid/expired-session {@code ValidationException}
 * verified live during the Phase 6 spike is treated this way — an unrelated
 * {@code ValidationException} (e.g. malformed input) must not destroy an otherwise-healthy
 * session mapping (post-approval correction).
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    // Grounding-chunk count for a single generated answer — not client-configurable
    // (Docs/API.md §20's request has no limit/filters field, unlike /search).
    private static final int NUMBER_OF_RESULTS = 8;

    // Cleanup safety net for our own mapping row, independent of whatever TTL Bedrock applies
    // to the underlying session itself (Docs/ARCHITECTURE.md §8.2: don't assume one).
    private static final Duration SESSION_MAPPING_TTL = Duration.ofHours(24);

    // Matches the exact wording confirmed live during the Phase 6 spike for an
    // invalid/expired Bedrock session — e.g. "Session with Id 00000000-0000-0000-0000-000000000000
    // is not valid. Please check and try again." A ValidationException with any other message
    // (malformed input, unsupported content, etc.) is a real validation failure unrelated to
    // session validity and must not expire a healthy session mapping.
    private static final Pattern INVALID_SESSION_MESSAGE =
            Pattern.compile("Session with Id .+ is not valid", Pattern.CASE_INSENSITIVE);

    private final BedrockAgentRuntimeClient bedrockAgentRuntimeClient;
    private final KnowledgeBaseRetriever retriever;
    private final DocumentRepository documentRepository;
    private final AskSessionRepository askSessionRepository;
    private final String knowledgeBaseId;
    private final String askModelArn;

    private static final int MAX_FOUND_FILES = 3;

    public AskService(BedrockAgentRuntimeClient bedrockAgentRuntimeClient, KnowledgeBaseRetriever retriever,
                       DocumentRepository documentRepository, AskSessionRepository askSessionRepository) {
        this.bedrockAgentRuntimeClient = bedrockAgentRuntimeClient;
        this.retriever = retriever;
        this.documentRepository = documentRepository;
        this.askSessionRepository = askSessionRepository;
        this.knowledgeBaseId = System.getenv("KNOWLEDGE_BASE_ID");
        this.askModelArn = System.getenv("ASK_MODEL_ARN");
    }

    public AskResponse ask(String userId, AskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new InvalidRequestException("question is required");
        }

        String applicationSessionId = blankToNull(request.sessionId());
        String bedrockSessionId = null;
        if (applicationSessionId != null) {
            // Resolution is always scoped to this authenticated user's own partition — a
            // sessionId belonging to another user, or already expired/deleted, simply doesn't
            // resolve, indistinguishably (Phase 6 amendment).
            AskSession existing = askSessionRepository.findByUserAndSessionId(userId, applicationSessionId)
                    .orElseThrow(AskSessionExpiredException::new);
            bedrockSessionId = existing.getBedrockSessionId();
        }

        // Preflight: same tenant filter, same relevance gate as /search.
        boolean followUp = bedrockSessionId != null;
        List<RelevantDocument> relevant = RelevantDocument.fromChunks(
                retriever.retrieveRelevant(RetrievalFilters.forUser(userId), request.question(), NUMBER_OF_RESULTS));

        if (relevant.isEmpty() && !followUp) {
            // Nothing relevant to ground an answer in: don't call the model at all.
            return new AskResponse(AskPrompt.NO_ANSWER, applicationSessionId, List.of());
        }
        if (!relevant.isEmpty() && FindIntent.isFindRequest(request.question())) {
            return findResponse(userId, applicationSessionId, relevant);
        }

        // Generation sees only the documents that passed the gate (or, for a follow-up that didn't
        // match on its own, the user's whole corpus - see the class doc).
        Set<String> allowedDocumentIds = relevant.isEmpty() ? null : documentIds(relevant);
        RetrievalFilter filter = allowedDocumentIds == null
                ? RetrievalFilters.forUser(userId)
                : RetrievalFilters.forUserAndDocuments(userId, allowedDocumentIds);

        RetrieveAndGenerateRequest.Builder requestBuilder = RetrieveAndGenerateRequest.builder()
                .input(RetrieveAndGenerateInput.builder().text(request.question()).build())
                .retrieveAndGenerateConfiguration(RetrieveAndGenerateConfiguration.builder()
                        .type(RetrieveAndGenerateType.KNOWLEDGE_BASE)
                        .knowledgeBaseConfiguration(KnowledgeBaseRetrieveAndGenerateConfiguration.builder()
                                .knowledgeBaseId(knowledgeBaseId)
                                .modelArn(askModelArn)
                                .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                                        .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                                .numberOfResults(NUMBER_OF_RESULTS)
                                                .filter(filter)
                                                .build())
                                        .build())
                                .generationConfiguration(GenerationConfiguration.builder()
                                        .promptTemplate(PromptTemplate.builder()
                                                .textPromptTemplate(AskPrompt.TEMPLATE)
                                                .build())
                                        .build())
                                .build())
                        .build());
        if (bedrockSessionId != null) {
            requestBuilder.sessionId(bedrockSessionId);
        }

        RetrieveAndGenerateResponse response;
        try {
            response = bedrockAgentRuntimeClient.retrieveAndGenerate(requestBuilder.build());
        } catch (ThrottlingException e) {
            log.warn("Bedrock RetrieveAndGenerate throttled", e);
            throw new RetrievalUnavailableException("The service is temporarily busy. Please retry shortly.", true);
        } catch (ValidationException e) {
            if (bedrockSessionId != null && isInvalidSessionError(e)) {
                log.warn("Bedrock rejected session {} for application session {} as invalid/expired; expiring it",
                        bedrockSessionId, applicationSessionId, e);
                askSessionRepository.delete(userId, applicationSessionId);
                throw new AskSessionExpiredException();
            }
            // Either there was no session involved, or this ValidationException is unrelated
            // to session validity (e.g. malformed input) — the session mapping, if any, is
            // left untouched. A healthy session must survive an unrelated validation failure.
            log.warn("Bedrock RetrieveAndGenerate rejected the request", e);
            throw new InvalidRequestException("Could not process the question.");
        } catch (SdkException e) {
            log.error("Bedrock RetrieveAndGenerate failed", e);
            throw new RetrievalUnavailableException("Ask is temporarily unavailable. Please try again.", false);
        }

        String resolvedApplicationSessionId = persistSessionMapping(userId, applicationSessionId, response.sessionId());
        String answer = response.output().text();
        // A refusal has no sources - whatever was retrieved behind it isn't evidence for anything.
        List<Citation> citations = AskPrompt.isNoAnswer(answer)
                ? List.of()
                : resolveCitations(userId, response, allowedDocumentIds);

        return new AskResponse(answer, resolvedApplicationSessionId, citations);
    }

    /** Answers "do I have ...?" from real metadata: the relevant documents' filenames. No model call,
     * so no Bedrock session is created or touched - an existing application session id is returned
     * unchanged, and a first turn returns none. */
    private AskResponse findResponse(String userId, String applicationSessionId, List<RelevantDocument> relevant) {
        List<Citation> citations = new ArrayList<>();
        for (RelevantDocument candidate : relevant) {
            if (citations.size() >= MAX_FOUND_FILES) {
                break;
            }
            documentRepository.findByUserAndDocumentId(userId, candidate.documentId()).ifPresent(document ->
                    citations.add(new Citation("c" + (citations.size() + 1), document.getDocumentId(), document.getFileName(),
                            document.getMediaCategory(), document.getMimeType(), candidate.snippet(), candidate.mediaTimestamp())));
        }
        if (citations.isEmpty()) {
            return new AskResponse(AskPrompt.NO_ANSWER, applicationSessionId, List.of());
        }

        List<String> names = citations.stream().map(citation -> "\u201c" + citation.fileName() + "\u201d").toList();
        String answer = citations.size() == 1
                ? "I found 1 file in your memories that matches: " + names.get(0) + "."
                : "I found " + citations.size() + " files in your memories that match: "
                        + String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1) + ".";
        return new AskResponse(answer, applicationSessionId, citations);
    }

    private static Set<String> documentIds(List<RelevantDocument> relevant) {
        Set<String> ids = new LinkedHashSet<>();
        relevant.forEach(document -> ids.add(document.documentId()));
        return ids;
    }

    /** A document that survived the relevance gate, with the display fields of its best chunk. */
    record RelevantDocument(String documentId, String snippet, MediaTimestamp mediaTimestamp) {

        /** One entry per document, in Bedrock's descending-score order (its best chunk first). */
        static List<RelevantDocument> fromChunks(List<KnowledgeBaseRetrievalResult> chunks) {
            Map<String, RelevantDocument> byDocument = new LinkedHashMap<>();
            for (KnowledgeBaseRetrievalResult chunk : chunks) {
                RetrievalContentMapper.ChunkRef ref = RetrievalContentMapper.ChunkRef.of(chunk);
                String documentId = RetrievalContentMapper.extractDocumentId(ref);
                if (documentId == null || byDocument.containsKey(documentId)) {
                    continue;
                }
                byDocument.put(documentId, new RelevantDocument(documentId, RetrievalContentMapper.resolveSnippet(ref),
                        RetrievalContentMapper.resolveMediaTimestamp(ref.metadata())));
            }
            return List.copyOf(byDocument.values());
        }
    }

    /** {@code allowedDocumentIds == null} means "no gate result to restrict to" (follow-up turns). */
    private List<Citation> resolveCitations(String userId, RetrieveAndGenerateResponse response, Set<String> allowedDocumentIds) {
        List<AskCitationMapper.DedupedCitation> deduped = AskCitationMapper.dedupeCitations(response.citations());

        List<Citation> citations = new ArrayList<>();
        int index = 1;
        for (AskCitationMapper.DedupedCitation candidate : deduped) {
            if (allowedDocumentIds != null && !allowedDocumentIds.contains(candidate.documentId())) {
                continue;
            }
            Document document = documentRepository.findByUserAndDocumentId(userId, candidate.documentId()).orElse(null);
            if (document == null) {
                // Same defensive skip as /search: the document was deleted after indexing, or
                // (should never happen given the server-side filter) belongs to another user.
                continue;
            }
            citations.add(new Citation("c" + index, document.getDocumentId(), document.getFileName(),
                    document.getMediaCategory(), document.getMimeType(), candidate.snippet(), candidate.mediaTimestamp()));
            index++;
        }
        return citations;
    }

    /** Creates the mapping on a first turn, or refreshes its TTL on a continuing one. The
     * application {@code sessionId} returned to the client never changes for the life of a
     * conversation — only the DynamoDB row backing it gets touched. */
    private String persistSessionMapping(String userId, String existingApplicationSessionId, String bedrockSessionId) {
        String applicationSessionId = existingApplicationSessionId != null
                ? existingApplicationSessionId
                : UUID.randomUUID().toString();

        AskSession session = new AskSession();
        session.setPk(DocumentKeys.userPartitionKey(userId));
        session.setSk(AskSessionRepository.sortKey(applicationSessionId));
        session.setApplicationSessionId(applicationSessionId);
        session.setUserId(userId);
        session.setBedrockSessionId(bedrockSessionId);
        session.setUpdatedAt(Instant.now().toString());
        session.setExpiresAt(Instant.now().plus(SESSION_MAPPING_TTL).getEpochSecond());
        askSessionRepository.save(session);

        return applicationSessionId;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static boolean isInvalidSessionError(ValidationException e) {
        String message = e.getMessage();
        return message != null && INVALID_SESSION_MESSAGE.matcher(message).find();
    }
}
