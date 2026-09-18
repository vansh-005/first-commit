package com.memorylayer.api.document;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNameSanitizerTest {

    @Test
    void keepsSafeFileNameUnchanged() {
        assertEquals("internship-offer.pdf", FileNameSanitizer.sanitize("internship-offer.pdf"));
    }

    @Test
    void stripsPathSeparatorsToPreventKeyTraversal() {
        assertEquals("passwd", FileNameSanitizer.sanitize("../../etc/passwd"));
        assertEquals("file.txt", FileNameSanitizer.sanitize("C:\\Users\\me\\file.txt"));
    }

    @Test
    void replacesUnsafeCharacters() {
        assertEquals("my_resume__final__.pdf", FileNameSanitizer.sanitize("my resume (final)!.pdf"));
    }

    @Test
    void blankResultFallsBackToDefaultName() {
        assertEquals("file", FileNameSanitizer.sanitize(""));
    }
}
