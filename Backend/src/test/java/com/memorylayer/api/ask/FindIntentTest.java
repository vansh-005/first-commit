package com.memorylayer.api.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FindIntentTest {

    @Test
    void recognisesExistenceAndLocateQuestions() {
        for (String question : new String[]{
                "do I have numerical methods assignment?",
                "Do I have the NumericalMethods assignment",
                "did I upload my passport scan?",
                "Is there a lease agreement in my memories?",
                "find my passport scan",
                "Show me my offer letter",
                "where is my internship offer?",
                "which documents do I have about AWS",
                "what files did I upload about fading"}) {
            assertThat(FindIntent.isFindRequest(question)).as(question).isTrue();
        }
    }

    @Test
    void neverTreatsAFactSeekingContentQuestionAsAFindRequest() {
        for (String question : new String[]{
                "How much AWS credit did I have?",
                "What did my internship offer say about relocation?",
                "When does my laptop warranty end?",
                "Did I save how much rent I pay?",
                "Who is my landlord?",
                "Summarize my most recent recording",
                "  ",
                ""}) {
            assertThat(FindIntent.isFindRequest(question)).as(question).isFalse();
        }
        assertThat(FindIntent.isFindRequest(null)).isFalse();
    }
}
