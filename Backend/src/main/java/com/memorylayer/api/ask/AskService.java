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
 * <p><b>Conversational document context:</b> when the find path resolves file(s) it creates/updates
 * the application {@link AskSession} (even though no Bedrock session exists yet) and stores the
 * resolved document ids as server-owned context. A later turn in that conversation with no Bedrock
 * session to lean on runs generation scoped to {@code userId AND documentId IN context} - not globally
 * gated - so "explain this assignment" reads the file that was just found. Once generation returns a
 * Bedrock session id it is saved into the same application session (context preserved). Context is
 * re-checked against the authenticated user's own documents on every turn and can never come from the
 * client. A new find question always re-resolves globally (through the gate) and replaces the context.
 *
 * Follow-up turns (an existing session) are otherwise the one exception to the gate: a follow-up such as
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
    private static final int MAX_DISCOVERY_DOCUMENTS = 500;

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
        String question = request.question();

        String applicationSessionId = blankToNull(request.sessionId());
        AskSession existing = null;
        if (applicationSessionId != null) {
            // Resolution is always scoped to this authenticated user's own partition - a
            // sessionId belonging to another user, or already expired/deleted, simply doesn't
            // resolve, indistinguishably (Phase 6 amendment).
            existing = askSessionRepository.findByUserAndSessionId(userId, applicationSessionId)
                    .orElseThrow(AskSessionExpiredException::new);
        }
        String bedrockSessionId = existing == null ? null : blankToNull(existing.getBedrockSessionId());
        List<String> contextDocumentIds = existing == null ? List.of() : liveContextDocuments(userId, existing);
        boolean findRequest = FindIntent.isFindRequest(question);

        // A conversation whose only progress is a resolved file has no Bedrock context to lean on, so a
        // deictic follow-up ("explain this assignment") is scoped to that file rather than globally gated.
        if (!contextDocumentIds.isEmpty() && bedrockSessionId == null && !findRequest) {
            // State-based rule, not a phrase list: this is the first generation of a context-only conversation, and
            // live testing showed the file-anchored wording is the reliable formulation for exactly this state
            // (16/16 vs 12/16 unanchored) - so it is asked FIRST, once, instead of paying a failed attempt before it.
            return generate(userId, question, applicationSessionId, null,
                    new LinkedHashSet<>(contextDocumentIds), contextDocumentIds, true, true);
        }

        // File discovery: filenames are evidence chunk similarity can't see. An explicit request ("find ...", "do I
        // have ...") always matches the caller's own filenames; a bare topic becomes discovery only on a strong
        // filename match, otherwise it stays an ordinary grounded question.
        List<Document> nameMatches = findRequest
                ? DocumentNameMatcher.match(documentRepository.listReadyByUser(userId, MAX_DISCOVERY_DOCUMENTS), question, true)
                : bareTopicNameMatches(userId, question);
        boolean discovery = findRequest || !nameMatches.isEmpty();

        // Preflight: same tenant filter, same relevance gate as /search.
        boolean followUp = bedrockSessionId != null;
        List<RelevantDocument> relevant = RelevantDocument.fromChunks(
                retriever.retrieveRelevant(RetrievalFilters.forUser(userId), question, NUMBER_OF_RESULTS));

        if (discovery) {
            // Resolved before the relevance check, so a filename-only match can succeed and a lookup that finds nothing
            // on either signal is the deterministic no-answer (never a model call).
            return findResponse(userId, applicationSessionId, existing, nameMatches, relevant);
        }

        if (relevant.isEmpty()) {
            if (!followUp && contextDocumentIds.isEmpty()) {
                // Nothing relevant to ground an answer in: don't call the model.
                return new AskResponse(AskPrompt.NO_ANSWER, applicationSessionId, List.of());
            }
            if (!contextDocumentIds.isEmpty()) {
                // A follow-up that doesn't match globally on its own stays on the file(s) this conversation resolved.
                return generate(userId, question, applicationSessionId, bedrockSessionId,
                        new LinkedHashSet<>(contextDocumentIds), contextDocumentIds, true, false);
            }
            // A follow-up with only Bedrock history behind it: generation with the tenant filter alone.
            return generate(userId, question, applicationSessionId, bedrockSessionId, null, contextDocumentIds, false, false);
        }
        return generate(userId, question, applicationSessionId, bedrockSessionId, documentIds(relevant), contextDocumentIds, false, false);
    }

    /** Strong filename matches for a short topic phrase; empty (no listing at all) for anything that looks like a question. */
    private List<Document> bareTopicNameMatches(String userId, String question) {
        if (!DocumentNameMatcher.mightBeBareTopic(question)) {
            return List.of();
        }
        return DocumentNameMatcher.match(documentRepository.listReadyByUser(userId, MAX_DISCOVERY_DOCUMENTS), question, false);
    }

    /**
     * Runs {@code RetrieveAndGenerate}. {@code allowedDocumentIds} restricts both retrieval
     * ({@code userId AND documentId IN ...}) and the citations returned; {@code null} means "tenant filter
     * only" (a follow-up that matched nothing on its own). {@code contextDocumentIds} is carried into the
     * saved session unchanged. {@code contextScoped} marks a deictic follow-up ("explain this assignment") whose
     * documents come from the conversation's own context. {@code anchoredFirst} sends the file-anchored wording as the
     * FIRST (and only) attempt - used for the first generation of a context-only conversation, so no attempt is spent
     * on a wording known to be unreliable there. Otherwise see {@link #needsAnchoredRetry}.
     */
    private AskResponse generate(String userId, String question, String applicationSessionId, String bedrockSessionId,
                                 Set<String> allowedDocumentIds, List<String> contextDocumentIds, boolean contextScoped,
                                 boolean anchoredFirst) {
        RetrievalFilter filter = allowedDocumentIds == null
                ? RetrievalFilters.forUser(userId)
                : RetrievalFilters.forUserAndDocuments(userId, allowedDocumentIds);

        RetrieveAndGenerateResponse response = invokeGeneration(userId,
                anchoredFirst ? AskPrompt.anchoredRetry(question) : question, filter, applicationSessionId, bedrockSessionId);
        // An anchored-first attempt is already the recovery formulation in a fresh session: retrying it would repeat it.
        if (!anchoredFirst && needsAnchoredRetry(response, allowedDocumentIds != null, contextScoped)) {
            // Retried in a FRESH Bedrock session: measured live, a fresh anchored attempt succeeded every time, whereas
            // failures clustered deep inside long sessions. The retry's session id is the one saved below.
            log.info("ask_anchored_retry: first generation was a refusal/ungrounded although relevant documents are known; retrying once");
            response = invokeGeneration(userId, AskPrompt.anchoredRetry(question), filter, applicationSessionId, null);
        }

        // Saved into the SAME application session, so a conversation that began with a find keeps its id.
        String resolvedApplicationSessionId =
                persistSessionMapping(userId, applicationSessionId, response.sessionId(), contextDocumentIds);
        String answer = response.output().text();
        boolean refusal = AskPrompt.isNoAnswer(answer);
        if (refusal) {
            // Always our deterministic sentence - never Bedrock's canned "Sorry, I am unable to assist ...".
            answer = AskPrompt.NO_ANSWER;
        }
        // A refusal has no sources - whatever was retrieved behind it isn't evidence for anything.
        List<Citation> citations = refusal
                ? List.of()
                : resolveCitations(userId, response, allowedDocumentIds);
        if (contextScoped && citations.isEmpty() && !refusal) {
            // Retrieval was restricted to the conversation's resolved file(s), so an answer can only be grounded in
            // them even when Bedrock returned no reference objects for it (seen live) - cite the files themselves.
            citations = contextCitations(userId, contextDocumentIds);
        }

        return new AskResponse(answer, resolvedApplicationSessionId, citations);
    }

    /** The conversation's context files as source cards (no snippet - the answer wasn't tied to a chunk). */
    private List<Citation> contextCitations(String userId, List<String> contextDocumentIds) {
        List<Citation> citations = new ArrayList<>();
        for (String documentId : contextDocumentIds) {
            documentRepository.findByUserAndDocumentId(userId, documentId).ifPresent(document ->
                    citations.add(new Citation("c" + (citations.size() + 1), document.getDocumentId(), document.getFileName(),
                            document.getMediaCategory(), document.getMimeType(), "", null)));
        }
        return citations;
    }

    /**
     * One anchored retry, decided from structure only (our own refusal sentinel and the reference count - never the
     * user's wording). {@code RetrieveAndGenerate}'s internal retrieval is unreliable for short, vague messages
     * (measured live), so when we already KNOW relevant documents exist ({@code documentsKnown}: they passed the gate
     * or came from the conversation's context) a failure to use them is worth exactly one more try:
     * <ul>
     *   <li>context-scoped (deictic) turn: a refusal, or an answer with no grounding, retries - the user is asking
     *       about the file itself, so "describe the contents, then ..." is the right reformulation;</li>
     *   <li>ordinary gated turn: only a refusal that also retrieved <b>nothing</b> retries - that is retrieval
     *       failing, whereas a refusal that did see references is a legitimate "the file doesn't say", and an
     *       ungrounded non-refusal (e.g. answered from conversation history) is left as it is.</li>
     * </ul>
     */
    private static boolean needsAnchoredRetry(RetrieveAndGenerateResponse response, boolean documentsKnown, boolean contextScoped) {
        if (!documentsKnown) {
            return false;
        }
        boolean refused = AskPrompt.isNoAnswer(response.output().text());
        boolean noReferences = response.citations().stream()
                .noneMatch(citation -> citation.hasRetrievedReferences() && !citation.retrievedReferences().isEmpty());
        return contextScoped ? (refused || noReferences) : (refused && noReferences);
    }

    private RetrieveAndGenerateResponse invokeGeneration(String userId, String question, RetrievalFilter filter,
                                                         String applicationSessionId, String bedrockSessionId) {
        RetrieveAndGenerateRequest.Builder requestBuilder = RetrieveAndGenerateRequest.builder()
                .input(RetrieveAndGenerateInput.builder().text(question).build())
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

        try {
            return bedrockAgentRuntimeClient.retrieveAndGenerate(requestBuilder.build());
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
            // to session validity (e.g. malformed input) - the session mapping, if any, is
            // left untouched. A healthy session must survive an unrelated validation failure.
            log.warn("Bedrock RetrieveAndGenerate rejected the request", e);
            throw new InvalidRequestException("Could not process the question.");
        } catch (SdkException e) {
            log.error("Bedrock RetrieveAndGenerate failed", e);
            throw new RetrievalUnavailableException("Ask is temporarily unavailable. Please try again.", false);
        }
    }

    /** Answers a file-discovery question from real metadata: filename matches first, then the semantically relevant
     * documents. No model call, so no Bedrock session is created or touched - but the resolved files are stored as the
     * conversation's context in the application session (created here if this is the first turn), so a follow-up like
     * "explain this assignment" can retrieve from them. Snippets come only from a semantic hit, never invented. */
    private AskResponse findResponse(String userId, String applicationSessionId, AskSession existing,
                                     List<Document> nameMatches, List<RelevantDocument> relevant) {
        Map<String, RelevantDocument> semanticHits = new LinkedHashMap<>();
        relevant.forEach(hit -> semanticHits.put(hit.documentId(), hit));

        List<Citation> citations = new ArrayList<>();
        Set<String> cited = new LinkedHashSet<>();
        for (Document document : nameMatches) {
            if (citations.size() >= MAX_FOUND_FILES) {
                break;
            }
            RelevantDocument hit = semanticHits.get(document.getDocumentId());
            cited.add(document.getDocumentId());
            citations.add(new Citation("c" + (citations.size() + 1), document.getDocumentId(), document.getFileName(),
                    document.getMediaCategory(), document.getMimeType(), hit == null ? "" : hit.snippet(),
                    hit == null ? null : hit.mediaTimestamp()));
        }
        for (RelevantDocument candidate : relevant) {
            if (citations.size() >= MAX_FOUND_FILES) {
                break;
            }
            if (cited.contains(candidate.documentId())) {
                continue;
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

        List<String> resolvedIds = citations.stream().map(Citation::documentId).toList();
        String keptBedrockSessionId = existing == null ? null : blankToNull(existing.getBedrockSessionId());
        String sessionId = persistSessionMapping(userId, applicationSessionId, keptBedrockSessionId, resolvedIds);
        return new AskResponse(answer, sessionId, citations);
    }

    /** The session's context documents that still exist under THIS user's own partition. Anything that
     * doesn't resolve (deleted, or - defensively - not theirs) is dropped, so a session can never widen
     * retrieval beyond documents the authenticated user owns. */
    private List<String> liveContextDocuments(String userId, AskSession session) {
        List<String> stored = session.getContextDocumentIds();
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        return stored.stream()
                .filter(id -> documentRepository.findByUserAndDocumentId(userId, id).isPresent())
                .toList();
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

    /** Creates the mapping on a first turn, or updates it on a continuing one. The application
     * {@code sessionId} returned to the client never changes for the life of a conversation - only the
     * DynamoDB row backing it gets touched. {@code bedrockSessionId} may be null (a conversation that has
     * only resolved a file so far); {@code contextDocumentIds} is the server-owned document context. */
    private String persistSessionMapping(String userId, String existingApplicationSessionId, String bedrockSessionId,
                                         List<String> contextDocumentIds) {
        String applicationSessionId = existingApplicationSessionId != null
                ? existingApplicationSessionId
                : UUID.randomUUID().toString();

        AskSession session = new AskSession();
        session.setPk(DocumentKeys.userPartitionKey(userId));
        session.setSk(AskSessionRepository.sortKey(applicationSessionId));
        session.setApplicationSessionId(applicationSessionId);
        session.setUserId(userId);
        session.setBedrockSessionId(bedrockSessionId);
        session.setContextDocumentIds(contextDocumentIds == null || contextDocumentIds.isEmpty() ? null : List.copyOf(contextDocumentIds));
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
