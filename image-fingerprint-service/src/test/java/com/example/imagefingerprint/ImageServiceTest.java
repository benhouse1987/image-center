package com.example.imagefingerprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ImageServiceTest {

    private static final int TEST_IMG_WIDTH = 64;
    private static final int TEST_IMG_HEIGHT = 64;
    private static final int HASH_LENGTH = 16; // 64 bits / 4 bits per hex char

    // Helper method to create a simple image and return it as an InputStream
    private InputStream createImageInputStream(Color color, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    // Helper method to create a grayscale gradient image
    private InputStream createGradientImageInputStream(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int grayValue = (int) (((double)x / width) * 255);
                image.setRGB(x, y, new Color(grayValue, grayValue, grayValue).getRGB());
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Test
    void testFingerprint_SolidBlackImage() throws IOException {
        try (InputStream blackImageStream = createImageInputStream(Color.BLACK, TEST_IMG_WIDTH, TEST_IMG_HEIGHT)) {
            String fingerprint = ImageService.calculateFingerprint(blackImageStream);
            assertNotNull(fingerprint);
            assertEquals(HASH_LENGTH, fingerprint.length());
            // For a solid black image, all pixels are 0. After resize, they should still be 0.
            // Average will be 0. All pixels are >= average. So hash should be all '1's if using > average.
            // If using >= average for 1, and < for 0: average is 0. All pixels are 0. So 0 >= 0 is true. All '1's.
            // Our current logic: pixel > average ? '1' : '0'. If all pixels are 0, average is 0. 0 > 0 is false. So all '0's.
            assertEquals("0000000000000000", fingerprint);
        }
    }

    @Test
    void testFingerprint_SolidWhiteImage() throws IOException {
        try (InputStream whiteImageStream = createImageInputStream(Color.WHITE, TEST_IMG_WIDTH, TEST_IMG_HEIGHT)) {
            String fingerprint = ImageService.calculateFingerprint(whiteImageStream);
            assertNotNull(fingerprint);
            assertEquals(HASH_LENGTH, fingerprint.length());
            // For a solid white image, all pixels are 255. Average is 255.
            // pixel > average (255 > 255) is false. So all '0's.
            assertEquals("0000000000000000", fingerprint);
        }
    }

    @Test
    void testFingerprint_HalfBlackHalfWhiteImage() throws IOException {
        BufferedImage image = new BufferedImage(TEST_IMG_WIDTH, TEST_IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, TEST_IMG_WIDTH / 2, TEST_IMG_HEIGHT);
        g.setColor(Color.WHITE);
        g.fillRect(TEST_IMG_WIDTH / 2, 0, TEST_IMG_WIDTH / 2, TEST_IMG_HEIGHT);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        try (InputStream imageStream = new ByteArrayInputStream(baos.toByteArray())) {
            String fingerprint = ImageService.calculateFingerprint(imageStream);
            assertNotNull(fingerprint);
            assertEquals(HASH_LENGTH, fingerprint.length());
            // This is harder to predict exactly without running, but it shouldn't be all zeros or all F's.
            // The average will be around 127. Left half pixels (0) < avg, right half (255) > avg.
            // So, roughly half '0's and half '1's.
            // For an 8x8 grid: left 4 columns '0', right 4 columns '1'.
            // 00001111 00001111 ... (8 times)
            // 0F0F0F0F0F0F0F0F in hex if it was perfectly alternating like that per row.
            // Or if it's 00001111 repeated for all 8 rows, then each byte is 0F.
            // Let's calculate for 8x8:
            // Resized: left 4 columns black (0), right 4 columns white (255)
            // Sum = (32 * 0) + (32 * 255) = 32 * 255 = 8160
            // Average = 8160 / 64 = 127.5
            // Left half: 0 > 127.5 is false ('0')
            // Right half: 255 > 127.5 is true ('1')
            // Binary: 0000111100001111000011110000111100001111000011110000111100001111
            // Hex: 0F0F0F0F0F0F0F0F
             assertEquals("0f0f0f0f0f0f0f0f", fingerprint.toLowerCase());
        }
    }


    @Test
    void testFingerprint_GradientImage() throws IOException {
        try (InputStream gradientImageStream = createGradientImageInputStream(TEST_IMG_WIDTH, TEST_IMG_HEIGHT)) {
            String fingerprint = ImageService.calculateFingerprint(gradientImageStream);
            assertNotNull(fingerprint);
            assertEquals(HASH_LENGTH, fingerprint.length());
            // A gradient will have a mix of 0s and 1s. It shouldn't be all 0s or all Fs (all 1s).
            assertNotEquals("0000000000000000", fingerprint);
            assertNotEquals("ffffffffffffffff", fingerprint.toLowerCase());
        }
    }

    @Test
    void testCalculateFingerprint_NullFilePath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ImageService.calculateFingerprint((String) null);
        });
        assertEquals("File path cannot be null or empty.", exception.getMessage());
    }

    @Test
    void testCalculateFingerprint_EmptyFilePath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ImageService.calculateFingerprint("");
        });
        assertEquals("File path cannot be null or empty.", exception.getMessage());
    }

    @Test
    void testCalculateFingerprint_NonExistentFile(@TempDir Path tempDir) {
        File nonExistentFile = tempDir.resolve("nonexistent.jpg").toFile();
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ImageService.calculateFingerprint(nonExistentFile.getAbsolutePath());
        });
        // The cause should be FileNotFoundException or similar IO related.
        assertTrue(exception.getCause() instanceof java.io.FileNotFoundException);
    }

    @Test
    void testCalculateFingerprint_NullInputStream() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ImageService.calculateFingerprint((InputStream) null);
        });
        assertEquals("Image stream cannot be null for input stream", exception.getMessage());
    }

    @Test
    void testCalculateFingerprint_InvalidImageStream() throws IOException {
        // Create a stream with non-image data
        byte[] invalidData = "This is not an image".getBytes();
        try (InputStream invalidImageStream = new ByteArrayInputStream(invalidData)) {
           IOException exception = assertThrows(IOException.class, () -> {
                ImageService.calculateFingerprint(invalidImageStream);
           });
           assertTrue(exception.getMessage().startsWith("Could not decode image from input stream."));
        }
    }

    @Test
    void testCalculateFingerprint_EmptyImageStream() throws IOException {
        try (InputStream emptyImageStream = new ByteArrayInputStream(new byte[0])) {
            IOException exception = assertThrows(IOException.class, () -> {
                 ImageService.calculateFingerprint(emptyImageStream);
            });
            assertTrue(exception.getMessage().startsWith("Could not decode image from input stream."));
        }
    }

    // Tests for calculateSimilarity
    @Test
    void testCalculateSimilarity_IdenticalFingerprints() {
        String fp = "abcdef0123456789"; // 16 chars
        assertEquals(1.0, ImageService.calculateSimilarity(fp, fp), 0.0001);
    }

    @Test
    void testCalculateSimilarity_CompletelyDifferentFingerprints() {
        String fp1 = "0000000000000000";
        String fp2 = "ffffffffffffffff";
        assertEquals(0.0, ImageService.calculateSimilarity(fp1, fp2), 0.0001);
    }

    @Test
    void testCalculateSimilarity_HalfDifferentFingerprints() {
        String fp1 = "00000000ffffffff"; // 32 bits different (8 hex chars * 4 bits/char)
        String fp2 = "0000000000000000";
        // 32 different bits out of 64 total bits. Distance = 32/64 = 0.5. Similarity = 1 - 0.5 = 0.5
        assertEquals(0.5, ImageService.calculateSimilarity(fp1, fp2), 0.0001);
    }

    @Test
    void testCalculateSimilarity_OneBitDifference() {
        String fp1 = "0000000000000000"; // binary all zeros
        String fp2 = "0000000000000001"; // binary ...0001 (1 bit different from fp1)
        // 1 different bit out of 64. Distance = 1/64. Similarity = 1 - 1/64 = 63/64
        double expectedSimilarity = (double) (ImageService.TOTAL_BITS - 1) / ImageService.TOTAL_BITS;
        assertEquals(expectedSimilarity, ImageService.calculateSimilarity(fp1, fp2), 0.000001);
    }

    @Test
    void testCalculateSimilarity_InvalidLengthFingerprint1() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ImageService.calculateSimilarity("short", "0000000000000000");
        });
        assertEquals("Fingerprint 1 cannot be null, empty, or of incorrect length.", exception.getMessage());
    }

    @Test
    void testCalculateSimilarity_InvalidLengthFingerprint2() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ImageService.calculateSimilarity("0000000000000000", "longfingerprintvalue");
        });
        assertEquals("Fingerprint 2 cannot be null, empty, or of incorrect length.", exception.getMessage());
    }

    @Test
    void testCalculateSimilarity_NullFingerprint1() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ImageService.calculateSimilarity(null, "0000000000000000");
        });
        assertEquals("Fingerprint 1 cannot be null, empty, or of incorrect length.", exception.getMessage());
    }

    @Test
    void testCalculateSimilarity_InvalidHexFingerprint() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            ImageService.calculateSimilarity("000000000000000g", "0000000000000000"); // 'g' is not valid hex
        });
        assertEquals("Invalid fingerprint format. Fingerprints must be valid hexadecimal strings.", exception.getMessage());
    }
}
