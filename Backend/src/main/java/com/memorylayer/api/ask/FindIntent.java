package com.memorylayer.api.ask;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognises "do I have …?" / "find my …" style questions — questions about whether a <i>file</i>
 * exists, not about what is <i>inside</i> one.
 *
 * <p>These can't be answered by the language model: it only sees chunk text, never filenames or
 * upload metadata (verified live — with every retrieved chunk from {@code NumericalMethods_Assignment1.pdf}
 * it still replied "couldn't find" to "do I have numerical methods assignment?"). So Ask answers
 * them directly from the documents that passed the relevance gate, using their real metadata.
 *
 * <p>Deliberately narrow: only sentence-initial existence/locate phrasings, and never a question
 * that asks for a fact ("how much", "when", "what", …), which must go to grounded generation.
 */
final class FindIntent {

    private FindIntent() {
    }

    private static final List<Pattern> FIND_PATTERNS = List.of(
            Pattern.compile("^(do|did|have) (i|we) (still )?(have|got|save|saved|upload|uploaded|store|stored|keep|kept)\\b.*"),
            Pattern.compile("^(is|are) there (a|an|any|my|the)\\b.*"),
            // A determiner is optional for the unambiguous discovery verbs ("find numerical method assignment"), but
            // "show me how ...", "find out ..." and "find a summary of ..." are content requests, not file lookups.
            Pattern.compile("^(find|locate|show|search for|look for|pull up|bring up)( me)?+( up)?+ "
                    + "(?!(out|how|what|why|when|who|if|whether|a summary|the summary|summary)\\b).+"),
            Pattern.compile("^(open|get|fetch)( me)?( up)? (my|the|a|an|all|any)\\b.*"),
            Pattern.compile("^where('s| is| are| did i (put|save|store))\\b.*"),
            Pattern.compile("^(which|what) (files?|documents?|pdfs?|images?|photos?|pictures?|recordings?|notes?|videos?|screenshots?)"
                    + " (do i have|did i (upload|save|store))\\b.*"));

    // A fact-seeking question about content, even if it starts like an existence question.
    private static final Pattern FACT_SEEKING =
            Pattern.compile("\\b(how much|how many|how long|how old|what was|what is|what's|when|why|who)\\b");

    static boolean isFindRequest(String question) {
        if (question == null) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT).replace('’', '\'').strip()
                .replaceAll("[?.!\\s]+$", "")
                .replaceAll("\\s+", " ");
        if (normalized.isEmpty() || FACT_SEEKING.matcher(normalized).find()) {
            return false;
        }
        return FIND_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).matches());
    }
}
