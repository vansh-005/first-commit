package com.memorylayer.api.ask;

import com.memorylayer.api.document.Document;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentNameMatcherTest {

    private static Document named(String fileName) {
        Document document = new Document();
        document.setDocumentId(fileName);
        document.setFileName(fileName);
        return document;
    }

    private static List<String> match(String question, boolean explicit, String... fileNames) {
        return DocumentNameMatcher.match(Arrays.stream(fileNames).map(DocumentNameMatcherTest::named).toList(), question, explicit)
                .stream().map(Document::getFileName).toList();
    }

    @Test
    void normalisesCamelCaseSeparatorsExtensionsAndDigits() {
        assertThat(DocumentNameMatcher.nameTokens("NumericalMethods_Assignment1.pdf")).containsExactly("numerical", "method", "assignment", "1");
        assertThat(DocumentNameMatcher.nameTokens("internship-offer.v2.PDF")).containsExactly("internship", "offer", "v", "2");
        assertThat(DocumentNameMatcher.nameTokens("HTMLParser.txt")).containsExactly("html", "parser");
    }

    @Test
    void singularAndPluralAreEquivalentAndCaseIsIgnored() {
        assertThat(match("Find NUMERICAL METHOD ASSIGNMENTS", true, "NumericalMethods_Assignment1.pdf"))
                .containsExactly("NumericalMethods_Assignment1.pdf");
    }

    @Test
    void everyMeaningfulQueryTokenMustBeInTheFilename() {
        assertThat(match("find numerical methods assignment", true, "Numerical_Methods_Notes.pdf", "Assignment1.pdf")).isEmpty();
        assertThat(match("find my numerical assignment", true, "NumericalMethods_Assignment1.pdf")).hasSize(1);
    }

    @Test
    void fillerAndIntentWordsDoNotCountAsQueryTokens() {
        assertThat(match("do I have the numerical methods assignment file?", true, "NumericalMethods_Assignment1.pdf")).hasSize(1);
        assertThat(match("where is my lease?", true, "lease.pdf")).hasSize(1);
    }

    @Test
    void oneEditTypoIsToleratedOnlyOnLongTokensAndOnlyForExplicitDiscovery() {
        assertThat(match("find numericl methods asignment", true, "NumericalMethods_Assignment1.pdf")).hasSize(1);
        assertThat(match("find lese", true, "lease.pdf")).isEmpty(); // short token: exact only
        assertThat(match("numericl methods assigment", false, "NumericalMethods_Assignment1.pdf")).isEmpty(); // bare: exact only
    }

    @Test
    void moreSpecificNamesRankAboveLooserOnes() {
        assertThat(match("find numerical assignment", true, "Numerical_Assignment_Old_Draft.pdf", "NumericalAssignment.pdf"))
                .containsExactly("NumericalAssignment.pdf", "Numerical_Assignment_Old_Draft.pdf");
    }

    @Test
    void exactMatchesRankAboveTypoMatches() {
        // "assigment" is a typo for the first file's token but exact for the second
        assertThat(match("find numerical assigment", true, "NumericalAssignment.pdf", "Numerical_Assigment.pdf"))
                .containsExactly("Numerical_Assigment.pdf", "NumericalAssignment.pdf");
    }

    @Test
    void aBareTopicNeedsMoreThanOneLooseWordUnlessItIsTheWholeFilename() {
        assertThat(match("assignment", false, "NumericalMethods_Assignment1.pdf")).isEmpty();
        assertThat(match("passport", false, "passport.pdf")).hasSize(1);
        assertThat(match("numerical assignment", false, "NumericalMethods_Assignment1.pdf")).hasSize(1);
    }

    @Test
    void questionsAndLongTextAreNotBareTopics() {
        assertThat(DocumentNameMatcher.mightBeBareTopic("numerical methods assignment")).isTrue();
        assertThat(DocumentNameMatcher.mightBeBareTopic("what does my assignment say")).isFalse();
        assertThat(DocumentNameMatcher.mightBeBareTopic("explain this assignment")).isFalse();
        assertThat(DocumentNameMatcher.mightBeBareTopic("one two three four five six seven")).isFalse();
        assertThat(DocumentNameMatcher.mightBeBareTopic("  ")).isFalse();
    }

    @Test
    void oneEditDistanceCoversInsertDeleteSubstituteAndTranspose() {
        assertThat(DocumentNameMatcher.withinOneEdit("assignment", "asignment")).isTrue();
        assertThat(DocumentNameMatcher.withinOneEdit("assignment", "assignmant")).isTrue();
        assertThat(DocumentNameMatcher.withinOneEdit("assignment", "assignmetn")).isTrue();
        assertThat(DocumentNameMatcher.withinOneEdit("assignment", "asignmant")).isFalse();
    }
}
