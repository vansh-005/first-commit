package com.memorylayer.api.observability;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLogTest {

    @Test
    void emitsOneValidJsonLineWithLevelEventAndTimestamp() {
        String output = captureStdout(() -> StructuredLog.info("test_event", Map.of("documentId", "doc-1")));

        assertThat(output.strip()).matches("\\{.*}");
        assertThat(output).contains("\"level\":\"INFO\"");
        assertThat(output).contains("\"event\":\"test_event\"");
        assertThat(output).contains("\"timestamp\"");
        assertThat(output).contains("\"documentId\":\"doc-1\"");
    }

    @Test
    void warnAndErrorUseTheirOwnLevel() {
        assertThat(captureStdout(() -> StructuredLog.warn("e", Map.of()))).contains("\"level\":\"WARN\"");
        assertThat(captureStdout(() -> StructuredLog.error("e", Map.of()))).contains("\"level\":\"ERROR\"");
    }

    @Test
    void hashUserIdNeverReturnsTheRawValue() {
        String hash = StructuredLog.hashUserId("61d34d4a-c081-704b-e04d-d3574d0b3e41");
        assertThat(hash).isNotEqualTo("61d34d4a-c081-704b-e04d-d3574d0b3e41");
        assertThat(hash).doesNotContain("61d34d4a");
    }

    @Test
    void hashUserIdIsDeterministicForCorrelation() {
        String userId = "some-cognito-sub";
        assertThat(StructuredLog.hashUserId(userId)).isEqualTo(StructuredLog.hashUserId(userId));
    }

    @Test
    void hashUserIdOfNullIsNull() {
        assertThat(StructuredLog.hashUserId(null)).isNull();
    }

    private static String captureStdout(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }
}
