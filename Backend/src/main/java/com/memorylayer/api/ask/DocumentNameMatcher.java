package com.memorylayer.api.ask;

import com.memorylayer.api.document.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches a discovery query ("find numerical method assignment") against the <i>filenames</i> of the
 * authenticated user's own documents. Chunk similarity can't see filenames, so file discovery uses this as
 * additional, authoritative evidence next to the semantic candidates.
 *
 * <p>Generic by construction - no phrase or file is special-cased:
 * <ul>
 *   <li>filenames are normalised: extension dropped, split on camelCase / {@code _ - .} / letter-digit boundaries,
 *       lower-cased, light singular/plural stemming;</li>
 *   <li>safe filler words ("my", "the", "file", ...) - and, for explicit discovery, the intent words ("find",
 *       "do I have", ...) - are removed from the query;</li>
 *   <li><b>every</b> remaining query token must be represented in the filename (a strong match);</li>
 *   <li>a one-edit typo is tolerated only on tokens of {@value #MIN_TYPO_TOKEN_LENGTH}+ characters, and only for
 *       explicit discovery; exact matches always rank above typo matches.</li>
 * </ul>
 *
 * <p>It only ever sees documents the caller passes in, which come from the authenticated user's own partition.
 */
final class DocumentNameMatcher {

    static final int MIN_TYPO_TOKEN_LENGTH = 6;
    private static final int MAX_BARE_TOPIC_TOKENS = 6;

    /** Never carry meaning about which file is wanted. */
    private static final Set<String> FILLER = Set.of(
            "a", "an", "the", "my", "our", "any", "all", "some", "of", "file", "files", "document", "documents",
            "doc", "docs", "pdf", "pdfs");

    /** Extra words that only express the discovery intent itself; removed for explicit discovery only. */
    private static final Set<String> INTENT = Set.of(
            "do", "did", "have", "got", "i", "we", "still", "is", "are", "there", "find", "locate", "show", "me", "up",
            "open", "get", "fetch", "pull", "bring", "search", "for", "look", "where", "wheres", "which", "what", "in",
            "save", "saved", "upload", "uploaded", "store", "stored", "keep", "kept", "put", "about", "memories",
            "memory", "here", "that", "to");

    /** Words that make a bare phrase a question or an instruction rather than a topic. */
    private static final Set<String> NOT_A_TOPIC = Set.of(
            "what", "whats", "how", "when", "why", "who", "whom", "which", "where", "does", "do", "did", "is", "are",
            "was", "were", "can", "could", "would", "should", "explain", "summarize", "summarise", "tell", "describe",
            "list", "give", "compare", "translate", "read", "it", "this", "that", "these", "those");

    private DocumentNameMatcher() {
    }

    /** Cheap pre-check so ordinary content questions never trigger a document listing. */
    static boolean mightBeBareTopic(String question) {
        List<String> tokens = words(question);
        return !tokens.isEmpty() && tokens.size() <= MAX_BARE_TOPIC_TOKENS
                && tokens.stream().noneMatch(NOT_A_TOPIC::contains);
    }

    /**
     * Documents whose filename represents every meaningful token of the query, best first.
     *
     * @param explicit true for an explicit discovery request ("find ...", "do I have ..."); a bare topic
     *                 ({@code false}) is stricter: exact matches only, and more than a single loose word.
     */
    static List<Document> match(List<Document> ownedDocuments, String question, boolean explicit) {
        List<String> queryTokens = words(question).stream()
                .filter(word -> !FILLER.contains(word) && !(explicit && INTENT.contains(word)))
                .map(DocumentNameMatcher::stem)
                .distinct()
                .toList();
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        record Scored(Document document, int typoMatches, int unmatchedNameTokens) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Document document : ownedDocuments) {
            if (document.getFileName() == null) {
                continue;
            }
            List<String> nameTokens = nameTokens(document.getFileName());
            int typoMatches = 0;
            boolean allRepresented = true;
            for (String token : queryTokens) {
                if (nameTokens.contains(token)) {
                    continue;
                }
                if (explicit && token.length() >= MIN_TYPO_TOKEN_LENGTH
                        && nameTokens.stream().anyMatch(nameToken -> withinOneEdit(token, nameToken))) {
                    typoMatches++;
                    continue;
                }
                allRepresented = false;
                break;
            }
            if (!allRepresented) {
                continue;
            }
            if (!explicit && queryTokens.size() < 2 && !coversWholeName(queryTokens, nameTokens)) {
                continue; // one loose word is too weak to turn a topic into a file lookup, unless it IS the file's name
            }
            scored.add(new Scored(document, typoMatches, (int) nameTokens.stream().filter(t -> !queryTokens.contains(t)).count()));
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::typoMatches).thenComparingInt(Scored::unmatchedNameTokens))
                .map(Scored::document)
                .toList();
    }

    private static boolean coversWholeName(List<String> queryTokens, List<String> nameTokens) {
        return nameTokens.stream().filter(token -> !token.chars().allMatch(Character::isDigit)).allMatch(queryTokens::contains);
    }

    /** Lower-case alphanumeric words of free text. */
    private static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        String cleaned = text.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("'", "");
        List<String> words = new ArrayList<>();
        for (String word : cleaned.split("[^a-z0-9]+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }

    /** "NumericalMethods_Assignment1.pdf" -> [numerical, method, assignment, 1]. */
    static List<String> nameTokens(String fileName) {
        String stem = fileName.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "");
        String spaced = stem
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replaceAll("([0-9])([A-Za-z])", "$1 $2");
        return words(spaced).stream().map(DocumentNameMatcher::stem).collect(Collectors.toList());
    }

    /** Light singular normalisation: methods -> method, studies -> study; "class"/"analysis" are left alone. */
    static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us") && !word.endsWith("is")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    /** Damerau-Levenshtein distance <= 1: one insertion, deletion, substitution or adjacent transposition. */
    static boolean withinOneEdit(String a, String b) {
        int lengthDifference = a.length() - b.length();
        if (Math.abs(lengthDifference) > 1 || a.equals(b)) {
            return a.equals(b);
        }
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        if (lengthDifference == 0) {
            if (a.substring(i + 1).equals(b.substring(i + 1))) {
                return true; // substitution
            }
            return i + 1 < a.length() && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i)
                    && a.substring(i + 2).equals(b.substring(i + 2)); // transposition
        }
        String longer = lengthDifference > 0 ? a : b;
        String shorter = lengthDifference > 0 ? b : a;
        return longer.substring(i + 1).equals(shorter.substring(i)); // insertion / deletion
    }
}
