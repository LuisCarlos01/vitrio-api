package dev.vitrio.api.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ImageFormatTest {

    @Test
    void detectsJpegByMagicBytes() {
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(ImageFixtures.JPEG_BYTES).orElseThrow());
    }

    @Test
    void detectsPngByMagicBytes() {
        assertEquals(ImageFormat.PNG, ImageFormat.detect(ImageFixtures.PNG_BYTES).orElseThrow());
    }

    @Test
    void rejectsContentThatDoesNotMatchAnyAcceptedFormat() {
        assertTrue(ImageFormat.detect(ImageFixtures.PLAIN_TEXT_BYTES).isEmpty());
    }

    @Test
    void rejectsContentShorterThanTheShortestMagicBytes() {
        assertTrue(ImageFormat.detect(new byte[] {(byte) 0xFF}).isEmpty());
    }
}
