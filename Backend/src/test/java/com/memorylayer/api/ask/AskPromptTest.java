package com.memorylayer.api.ask;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AskPromptTest {

    @Test
    void containsThePlaceholdersAwsRequires() {
        // $search_results$ is required for every model; $output_format_instructions$ is what makes the
        // response carry citation references (verified live: omitting it returns zero references).
        assertThat(AskPrompt.TEMPLATE).contains("$search_results$").contains("$output_format_instructions$");
    }

    @Test
    void instructsTheModelToStayGroundedAndPersonalContextIsAllowed() {
        assertThat(AskPrompt.TEMPLATE)
                .contains("deliberately uploaded")
                .contains("own private account")
                .contains("personal details")
                .contains("Answer ONLY from the search results")
                .contains("do not give general advice")
                .contains(AskPrompt.NO_ANSWER);
    }

    @Test
    void recognisesItsOwnNoAnswerSentenceTolerantlyButNothingElse() {
        assertThat(AskPrompt.isNoAnswer(AskPrompt.NO_ANSWER)).isTrue();
        assertThat(AskPrompt.isNoAnswer("  I couldn’t find anything in your memories that answers that. ")).isTrue();
        assertThat(AskPrompt.isNoAnswer("I COULDN'T FIND ANYTHING IN YOUR MEMORIES THAT ANSWERS THAT.")).isTrue();
        assertThat(AskPrompt.isNoAnswer("You had $200 in AWS credits.")).isFalse();
        assertThat(AskPrompt.isNoAnswer(null)).isFalse();
    }
}
