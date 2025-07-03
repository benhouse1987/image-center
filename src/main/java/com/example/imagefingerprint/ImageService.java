package com.example.imagefingerprint;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private static final int HASH_WIDTH = 8; // For an 8x8 hash (64 bits)
    private static final int HASH_HEIGHT = 8;
    private static final int TOTAL_BITS = HASH_WIDTH * HASH_HEIGHT;

    private static BufferedImage resizeAndGrayscale(BufferedImage originalImage) throws IOException {
        BufferedImage thumbnailImage = Thumbnails.of(originalImage)
                .size(HASH_WIDTH, HASH_HEIGHT)
                // Requesting TYPE_BYTE_GRAY here is good, but we will ensure it by drawing onto a new image.
                .imageType(BufferedImage.TYPE_BYTE_GRAY)
                .asBufferedImage();

        if (thumbnailImage == null) {
            throw new IOException("Resizing or grayscaling with Thumbnailator failed, returned null.");
        }

        // Normalize the image by drawing it onto a new BufferedImage of the exact desired type and size.
        // This can help prevent issues with unconventional rasters from the original or thumbnailing process.
        BufferedImage finalImage = new BufferedImage(HASH_WIDTH, HASH_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = finalImage.createGraphics();
        try {
            g.drawImage(thumbnailImage, 0, 0, HASH_WIDTH, HASH_HEIGHT, null);
        } finally {
            g.dispose();
        }

        return finalImage;
    }

    private static String calculateBinaryHash(BufferedImage grayscaleImage) {
        // grayscaleImage is expected to be HASH_WIDTH x HASH_HEIGHT and TYPE_BYTE_GRAY
        long sum = 0;
        for (int y = 0; y < HASH_HEIGHT; y++) {
            for (int x = 0; x < HASH_WIDTH; x++) {
                // Extract gray value. For TYPE_BYTE_GRAY, R, G, and B components are the same.
                // getRGB() returns a packed sRGB value. We can extract one component.
                // (grayscaleImage.getRGB(x, y) & 0xFF) would give the blue component, which is fine for gray.
                sum += (grayscaleImage.getRGB(x, y) & 0xFF);
            }
        }
        long average = sum / TOTAL_BITS;

        StringBuilder hashBuilder = new StringBuilder(TOTAL_BITS);
        for (int y = 0; y < HASH_HEIGHT; y++) {
            for (int x = 0; x < HASH_WIDTH; x++) {
                hashBuilder.append((grayscaleImage.getRGB(x, y) & 0xFF) > average ? '1' : '0');
            }
        }
        return hashBuilder.toString();
    }

    private static String binaryToHex(String binaryString) {
        // Ensure the binary string length is a multiple of 4 for direct hex conversion
        // Pad with leading zeros if necessary, though for 64 bits it should be fine.
        if (binaryString.length() % 4 != 0) {
            int padding = 4 - (binaryString.length() % 4);
            StringBuilder sb = new StringBuilder(binaryString);
            for(int i=0; i<padding; i++) {
                sb.insert(0, '0');
            }
            binaryString = sb.toString();
        }
        BigInteger bigInt = new BigInteger(binaryString, 2);
        String hex = bigInt.toString(16);
        // Ensure the hex string is of expected length (e.g., 16 chars for 64 bits) by padding with leading zeros
        int expectedHexLength = TOTAL_BITS / 4;
        while(hex.length() < expectedHexLength){
            hex = "0" + hex;
        }
        return hex;
    }

    public static String calculateFingerprint(String filePath){
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }
        try (InputStream imageStream = new FileInputStream(filePath)) {
            return processImageStream(imageStream, "file path: " + filePath);
        } catch (Exception e) {
            logger.error("Error: {}", filePath, e);
            throw new RuntimeException(e);
        }
    }

    public static String calculateFingerprint(InputStream imageStream) throws IOException {
        return processImageStream(imageStream, "input stream");
    }

    private static String processImageStream(InputStream imageStream, String imageSourceDescription) throws IOException {
        if (imageStream == null) {
            throw new IllegalArgumentException("Image stream cannot be null for " + imageSourceDescription);
        }
        try {
            BufferedImage originalImage = ImageIO.read(imageStream);
            if (originalImage == null) {
                throw new IOException("Could not decode image from " + imageSourceDescription + ". The image format might not be supported or the stream is invalid/empty.");
            }
            BufferedImage grayscaleResizedImage = resizeAndGrayscale(originalImage);
            String binaryHash = calculateBinaryHash(grayscaleResizedImage);
            return binaryHash; // Return binary hash directly instead of converting to hex
        } finally {
            try {
                imageStream.close();
            } catch (IOException e) {
                logger.warn("Failed to close image stream from {}: {}", imageSourceDescription, e.getMessage(), e);
            }
        }
    }

    public static double calculateSimilarity(String fingerprint1Binary, String fingerprint2Binary) {
        if (fingerprint1Binary == null || fingerprint1Binary.isEmpty() || fingerprint1Binary.length() != TOTAL_BITS) {
            throw new IllegalArgumentException("Fingerprint 1 cannot be null, empty, or of incorrect length.");
        }
        if (fingerprint2Binary == null || fingerprint2Binary.isEmpty() || fingerprint2Binary.length() != TOTAL_BITS) {
            throw new IllegalArgumentException("Fingerprint 2 cannot be null, empty, or of incorrect length.");
        }

        try {
            // Convert binary strings to long values for bitwise operations
            long hash1 = new BigInteger(fingerprint1Binary, 2).longValue();
            long hash2 = new BigInteger(fingerprint2Binary, 2).longValue();

            // XOR the two hashes to get bits that differ
            long xorResult = hash1 ^ hash2;

            // Count the number of 1 bits in the XOR result (Hamming distance)
            int hammingDistance = Long.bitCount(xorResult);

            // Calculate similarity as 1 - (hamming distance / total bits)
            double normalizedDistance = (double) hammingDistance / TOTAL_BITS;
            return 1.0 - normalizedDistance;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid fingerprint format. Fingerprints must be valid binary strings.", e);
        }
    }
   

    /**
     * A simple class to hold a pair of hashes.
     */
    public static class HashPair {
        public final String hash1;
        public final String hash2;
        public final double similarity;

        public HashPair(String hash1, String hash2, double similarity) {
            this.hash1 = hash1;
            this.hash2 = hash2;
            this.similarity = similarity;
        }

        @Override
        public String toString() {
            return "HashPair{" +
                    "hash1='" + hash1 + '\'' +
                    ", hash2='" + hash2 + '\'' +
                    ", similarity=" + similarity +
                    '}';
        }

        // Optional: equals and hashCode if these pairs are stored in sets or used as map keys.
        // For now, not strictly necessary for the requested functionality.
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HashPair hashPair = (HashPair) o;
            // Consider pairs (a,b) and (b,a) as equal if order doesn't matter.
            // For now, strict equality.
            return Double.compare(hashPair.similarity, similarity) == 0 &&
                    ((hash1.equals(hashPair.hash1) && hash2.equals(hashPair.hash2)) ||
                     (hash1.equals(hashPair.hash2) && hash2.equals(hashPair.hash1)));
        }

        @Override
        public int hashCode() {
            // Simple hash combining, order independent for hash1 and hash2
            int h1 = hash1.hashCode();
            int h2 = hash2.hashCode();
            return (h1 < h2 ? java.util.Objects.hash(h1, h2, similarity) : java.util.Objects.hash(h2, h1, similarity));
        }
    }

    /**
     * Finds pairs of hashes from a list that have a similarity score above a given threshold.
     *
     * @param hashList The list of hash strings to compare.
     * @param similarityThreshold The minimum similarity score for a pair to be included.
     * @return A list of HashPair objects representing the similar hash pairs.
     */
    public static java.util.List<HashPair> findSimilarHashes(java.util.List<String> hashList, double similarityThreshold) {
        java.util.List<HashPair> similarPairs = new java.util.ArrayList<>();
        if (hashList == null || hashList.size() < 2) {
            return similarPairs; // Not enough hashes to form a pair
        }

        for (int i = 0; i < hashList.size(); i++) {
            for (int j = i + 1; j < hashList.size(); j++) {
                String hash1 = hashList.get(i);
                String hash2 = hashList.get(j);

                // Basic validation for individual hashes can be done here if necessary,
                // e.g., checking length or format, though calculateSimilarity also does this.
                // For now, assume calculateSimilarity handles invalid hash formats by throwing an exception.
                try {
                    double similarity = calculateSimilarity(hash1, hash2);
                    if (similarity > similarityThreshold) {
                        similarPairs.add(new HashPair(hash1, hash2, similarity));
                    }
                } catch (IllegalArgumentException e) {
                    // Log or handle invalid hash strings if needed, or let it propagate.
                    // For now, we'll skip pairs with invalid hashes.
                    logger.warn("Could not compare hashes '{}' and '{}' due to invalid format: {}", hash1, hash2, e.getMessage());
                }
            }
        }
        return similarPairs;
    }

    public static void main(String[] args){
        String hash1=   calculateFingerprint("C:\\Users\\Administrator\\Downloads\\testpic\\3.JPG");
        String hash2=   calculateFingerprint("C:\\Users\\Administrator\\Downloads\\testpic\\4.JPG");
        logger.info("hash1 {}",hash1);
        logger.info("hash2 {}",hash2);
        double similarity=calculateSimilarity(hash1, hash2);
        logger.info("similarity {}",similarity);
        
    }
}
