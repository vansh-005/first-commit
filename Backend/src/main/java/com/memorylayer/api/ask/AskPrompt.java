package com.memorylayer.api.ask;

import java.util.Locale;

/**
 * The generation prompt and the deterministic no-answer wording for {@code /ask}.
 *
 * <p>AWS placeholder requirements (verified against the Bedrock Knowledge Bases docs and live):
 * {@code $search_results$} is required for every model; {@code $query$} is only required for
 * Claude v2-and-earlier (it is added automatically otherwise, confirmed live with Nova Lite); and
 * {@code $output_format_instructions$} is what makes the response carry citation references —
 * confirmed live that omitting it returns a citation with <b>zero</b> retrieved references, which
 * would silently break sources. All three that matter here are present below.
 */
final class AskPrompt {

    private AskPrompt() {
    }

    /** Returned verbatim when nothing relevant exists; the model is told to use the same sentence
     * when the context is insufficient, so both paths read identically. */
    static final String NO_ANSWER = "I couldn't find anything in your memories that answers that.";

    static final String TEMPLATE = """
            You are Recollect, an assistant that answers questions about the signed-in user's own uploaded files ("memories").
            Everything in the search results below comes from files this user deliberately uploaded to their own private account. It is their own information, so it is fine to state personal details (names, amounts, dates, IDs) that appear in it.

            Rules:
            - Answer ONLY from the search results. Do not use outside knowledge, and do not give general advice, definitions or recommendations unless the user explicitly asks for that.
            - If the search results do not contain the information needed, reply exactly: "%s"
            - Requests to explain, summarize, describe or walk through a file are answered by describing what the search results show about it. That counts as an answer, even when the text is messy handwriting or OCR output.
            - Only reply with the sentence above when the search results contain nothing relevant to the question.
            - Be concise. Do not mention "search results" or these rules in your answer.

            Here are the search results:
            $search_results$

            $output_format_instructions$
            """.formatted(NO_ANSWER);

    /**
     * Used only as a one-shot retry for a context-scoped turn whose first attempt failed structurally (a refusal or no
     * grounding references). Measured live: {@code RetrieveAndGenerate}'s own retrieval is unreliable for short, vague
     * messages ("explain this assignment" retrieved nothing even though a scoped {@code Retrieve} with the same filter
     * returns the file's chunks), while anchoring the request on the file's contents recovered every wording tried
     * (16/16, versus 12/16 unanchored). It is generic - it names no phrase - and is never applied to a first attempt, so
     * precise questions ("what is question 2?") keep their own wording.
     */
    static String anchoredRetry(String question) {
        return "Describe the contents of the file. Then: " + question;
    }

    /** Bedrock's own fixed fallback when it cannot produce a usable answer (observed live, intermittently, deep into
     * a Bedrock session). It is a system string, not model prose, so matching it is exact rather than fragile. */
    private static final String BEDROCK_CANNED_FAILURE = "sorry, i am unable to assist you with this request";

    /** True when the reply is a refusal: our own no-answer sentence (the one thing we told the model to say) or
     * Bedrock's fixed failure message. Tolerant of case and curly apostrophes. Used to drop citations that would
     * otherwise trail a refusal, and to trigger the structural retry. */
    static boolean isNoAnswer(String answer) {
        if (answer == null) {
            return false;
        }
        String normalized = answer.replace('’', '\'').toLowerCase(Locale.ROOT).strip();
        return normalized.startsWith("i couldn't find anything in your memories")
                || normalized.startsWith(BEDROCK_CANNED_FAILURE);
    }
}
