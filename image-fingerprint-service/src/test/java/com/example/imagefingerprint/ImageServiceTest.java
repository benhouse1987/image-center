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

    // Tests for findSimilarHashes
    @Test
    void testFindSimilarHashes_EmptyList() {
        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(java.util.Collections.emptyList(), 0.9);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindSimilarHashes_SingleHash() {
        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(java.util.Collections.singletonList("0000000000000000"), 0.9);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindSimilarHashes_NoSimilarPairs() {
        java.util.List<String> hashes = java.util.Arrays.asList(
                "0000000000000000", // All 0s
                "ffffffffffffffff", // All 1s (0% similarity)
                "0123456789abcdef"  // Random
        );
        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, 0.5);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindSimilarHashes_OneSimilarPair() {
        String hash1 = "0000000000000000";
        String hash2_similar = "0000000000000001"; // 1 bit diff (63/64 similarity)
        String hash3_different = "ffffffffffffffff";
        java.util.List<String> hashes = java.util.Arrays.asList(hash1, hash2_similar, hash3_different);

        // Threshold for 63/64 similarity (0.984375)
        double threshold = 0.98;
        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, threshold);

        assertEquals(1, result.size());
        ImageService.HashPair pair = result.get(0);
        // Check if the pair contains the two similar hashes, order might vary.
        assertTrue((pair.hash1.equals(hash1) && pair.hash2.equals(hash2_similar)) ||
                     (pair.hash1.equals(hash2_similar) && pair.hash2.equals(hash1)));
        assertEquals((double) (ImageService.TOTAL_BITS - 1) / ImageService.TOTAL_BITS, pair.similarity, 0.00001);
    }

    @Test
    void testFindSimilarHashes_MultipleSimilarPairs() {
        String h1 = "0000000000000000"; // base
        String h2_s1 = "0000000000000001"; // 1 bit diff from h1
        String h3_s1 = "0000000000000003"; // 2 bits diff from h1 (0011), 1 bit diff from h2_s1 (0001 vs 0011)
        String h4_d = "ffffffffffffffff"; // different

        java.util.List<String> hashes = java.util.Arrays.asList(h1, h2_s1, h3_s1, h4_d);
        double threshold = 0.96; // Allows for up to 2 bits difference (62/64 = 0.96875)

        java.util.List<ImageService.HashPair> results = ImageService.findSimilarHashes(hashes, threshold);
        assertEquals(3, results.size()); // (h1,h2_s1), (h1,h3_s1), (h2_s1,h3_s1)

        // Expected pairs and their similarities
        ImageService.HashPair expectedPair12 = new ImageService.HashPair(h1, h2_s1, ImageService.calculateSimilarity(h1, h2_s1));
        ImageService.HashPair expectedPair13 = new ImageService.HashPair(h1, h3_s1, ImageService.calculateSimilarity(h1, h3_s1));
        ImageService.HashPair expectedPair23 = new ImageService.HashPair(h2_s1, h3_s1, ImageService.calculateSimilarity(h2_s1, h3_s1));

        java.util.Set<ImageService.HashPair> resultSet = new java.util.HashSet<>(results);
        assertTrue(resultSet.contains(expectedPair12));
        assertTrue(resultSet.contains(expectedPair13));
        assertTrue(resultSet.contains(expectedPair23));
    }

    @Test
    void testFindSimilarHashes_AllIdentical() {
        String hash = "1234567890abcdef";
        java.util.List<String> hashes = java.util.Arrays.asList(hash, hash, hash);
        // With 3 identical hashes, there are 3 pairs: (0,1), (0,2), (1,2)
        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, 0.99);
        assertEquals(3, result.size());
        for (ImageService.HashPair pair : result) {
            assertEquals(hash, pair.hash1);
            assertEquals(hash, pair.hash2);
            assertEquals(1.0, pair.similarity, 0.00001);
        }
    }

    @Test
    void testFindSimilarHashes_BelowThreshold() {
        String hash1 = "0000000000000000";
        String hash2_just_below = "000000000000000f"; // 4 bits diff. Similarity = 60/64 = 0.9375
        java.util.List<String> hashes = java.util.Arrays.asList(hash1, hash2_just_below);

        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, 0.95); // Threshold is higher
        assertTrue(result.isEmpty());
    }

    @Test
    void testFindSimilarHashes_ThresholdIsMetExactly() {
        // Test case where similarity IS EQUAL to threshold. Current logic is "> threshold"
        // So this pair should NOT be included.
        String h1 = "0000000000000000";
        String h2 = "0000000000000001"; // Similarity (63/64) = 0.984375
        java.util.List<String> hashes = java.util.Arrays.asList(h1, h2);

        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, 0.984375);
        assertTrue(result.isEmpty(), "Similarity equal to threshold should not be included due to '>' comparison.");

        // If we test with a slightly lower threshold, it should be included.
        result = ImageService.findSimilarHashes(hashes, 0.984374);
        assertEquals(1, result.size());
    }

    @Test
    void testFindSimilarHashes_ThresholdZero() {
        String h1 = "0000000000000000";
        String h2 = "1111111111111111";
        String h3 = "ffffffffffffffff";
        java.util.List<String> hashes = java.util.Arrays.asList(h1, h2, h3);
        // All 3 unique pairs should be returned: (h1,h2), (h1,h3), (h2,h3)
        // as their similarities will be > 0 (unless identical, which they are not).
        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, 0.0);
        assertEquals(3, result.size());
    }

    @Test
    void testFindSimilarHashes_ThresholdOne() {
        String h_identical1 = "abcdef0123456789";
        String h_identical2 = "abcdef0123456789";
        String h_different = "0000000000000000";
        java.util.List<String> hashes = java.util.Arrays.asList(h_identical1, h_identical2, h_different);

        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, 1.0);
        // Only the pair (h_identical1, h_identical2) should be found, as similarity is 1.0.
        // But current logic is "> threshold", so 1.0 > 1.0 is false. No pairs.
        assertTrue(result.isEmpty(), "No pairs should be found if threshold is 1.0 and comparison is strictly '>'");

        // If we want to include 1.0, the threshold should be slightly less, e.g., 0.99999
        result = ImageService.findSimilarHashes(hashes, 0.99999);
        assertEquals(1, result.size());
        assertEquals(h_identical1, result.get(0).hash1);
        assertEquals(h_identical2, result.get(0).hash2);
        assertEquals(1.0, result.get(0).similarity, 0.000001);
    }

    @Test
    void testFindSimilarHashes_WithInvalidHashInList() {
        String validHash1 = "0000000000000000";
        String invalidHash = "invalidhash"; // Too short, not hex
        String validHash2 = "0000000000000001"; // Similar to validHash1
        java.util.List<String> hashes = java.util.Arrays.asList(validHash1, invalidHash, validHash2);

        // The method should log a warning for pairs involving invalidHash and skip them,
        // but still find the valid pair (validHash1, validHash2).
        java.util.List<ImageService.HashPair> result = ImageService.findSimilarHashes(hashes, 0.98);
        assertEquals(1, result.size());
        ImageService.HashPair pair = result.get(0);
        assertTrue((pair.hash1.equals(validHash1) && pair.hash2.equals(validHash2)) ||
                     (pair.hash1.equals(validHash2) && pair.hash2.equals(validHash1)));
        assertEquals(ImageService.calculateSimilarity(validHash1, validHash2), pair.similarity, 0.00001);
    }
}
