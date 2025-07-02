package com.example.imagefingerprint;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
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

    private BufferedImage resizeAndGrayscale(BufferedImage originalImage) throws IOException {
        BufferedImage resizedImage = Thumbnails.of(originalImage)
                .size(HASH_WIDTH, HASH_HEIGHT)
                .imageType(BufferedImage.TYPE_BYTE_GRAY) // Convert to grayscale during resize
                .asBufferedImage();
        if (resizedImage == null) {
            throw new IOException("Resizing or grayscaling failed, thumbnailator returned null.");
        }
        return resizedImage;
    }

    private String calculateBinaryHash(BufferedImage grayscaleImage) {
        long sum = 0;
        for (int y = 0; y < HASH_HEIGHT; y++) {
            for (int x = 0; x < HASH_WIDTH; x++) {
                sum += grayscaleImage.getRaster().getSample(x, y, 0); // TYPE_BYTE_GRAY has one band
            }
        }
        long average = sum / TOTAL_BITS;

        StringBuilder hashBuilder = new StringBuilder(TOTAL_BITS);
        for (int y = 0; y < HASH_HEIGHT; y++) {
            for (int x = 0; x < HASH_WIDTH; x++) {
                hashBuilder.append(grayscaleImage.getRaster().getSample(x, y, 0) > average ? '1' : '0');
            }
        }
        return hashBuilder.toString();
    }

    private String binaryToHex(String binaryString) {
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

    public String calculateFingerprint(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }
        try (InputStream imageStream = new FileInputStream(filePath)) {
            return processImageStream(imageStream, "file path: " + filePath);
        } catch (FileNotFoundException e) {
            logger.error("File not found at path: {}", filePath, e);
            throw e;
        }
    }

    public String calculateFingerprint(InputStream imageStream) throws IOException {
        return processImageStream(imageStream, "input stream");
    }

    private String processImageStream(InputStream imageStream, String imageSourceDescription) throws IOException {
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
            return binaryToHex(binaryHash);
        } finally {
            try {
                imageStream.close();
            } catch (IOException e) {
                logger.warn("Failed to close image stream from {}: {}", imageSourceDescription, e.getMessage(), e);
            }
        }
    }

    public double calculateSimilarity(String fingerprint1Hex, String fingerprint2Hex) {
        if (fingerprint1Hex == null || fingerprint1Hex.isEmpty() || fingerprint1Hex.length() != TOTAL_BITS / 4) {
            throw new IllegalArgumentException("Fingerprint 1 cannot be null, empty, or of incorrect length.");
        }
        if (fingerprint2Hex == null || fingerprint2Hex.isEmpty() || fingerprint2Hex.length() != TOTAL_BITS / 4) {
            throw new IllegalArgumentException("Fingerprint 2 cannot be null, empty, or of incorrect length.");
        }

        try {
            String binary1 = new BigInteger(fingerprint1Hex, 16).toString(2);
            String binary2 = new BigInteger(fingerprint2Hex, 16).toString(2);

            // Pad with leading zeros to ensure fixed length for Hamming distance
            while (binary1.length() < TOTAL_BITS) {
                binary1 = "0" + binary1;
            }
            while (binary2.length() < TOTAL_BITS) {
                binary2 = "0" + binary2;
            }

            if (binary1.length() != binary2.length()) {
                 // This should not happen if hex fingerprints were of correct length and padded correctly
                throw new IllegalArgumentException("Binary fingerprints have different lengths after conversion, which is unexpected.");
            }

            int hammingDistance = 0;
            for (int i = 0; i < TOTAL_BITS; i++) {
                if (binary1.charAt(i) != binary2.charAt(i)) {
                    hammingDistance++;
                }
            }

            double normalizedDistance = (double) hammingDistance / TOTAL_BITS;
            return 1.0 - normalizedDistance;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid fingerprint format. Fingerprints must be valid hexadecimal strings.", e);
        }
    }
}
