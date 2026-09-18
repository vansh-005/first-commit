package com.memorylayer.api.document;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MediaCategoryTest {

    @ParameterizedTest
    @CsvSource({
            "image/png, IMAGE",
            "image/jpeg, IMAGE",
            "video/mp4, VIDEO",
            "audio/mpeg, AUDIO",
            "application/pdf, DOCUMENT",
            "text/plain, DOCUMENT",
            "application/msword, DOCUMENT",
            "application/zip, OTHER",
    })
    void mapsMimeTypeToCategory(String mimeType, MediaCategory expected) {
        assertEquals(expected, MediaCategory.fromMimeType(mimeType));
    }

    @Test
    void nullMimeTypeMapsToOther() {
        assertEquals(MediaCategory.OTHER, MediaCategory.fromMimeType(null));
    }
}
