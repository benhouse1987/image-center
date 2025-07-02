package com.example.imagefingerprint;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.AverageHash;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageService.class);
    private final HashingAlgorithm hasher = new AverageHash(64); // Using AverageHash with a 64-bit hash

    public String calculateFingerprint(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }
        try (InputStream imageStream = new FileInputStream(filePath)) {
            return calculateFingerprint(imageStream);
        } catch (FileNotFoundException e) {
            logger.error("File not found at path: {}", filePath, e);
            throw e; // Re-throw to be handled by controller
        }
    }

    public String calculateFingerprint(InputStream imageStream) throws IOException {
        if (imageStream == null) {
            // This case should ideally be handled by the caller if it's an internal call
            // For external calls like controller, it's fine.
            throw new IllegalArgumentException("Image stream cannot be null.");
        }
        try {
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                // This can happen if the stream is empty, not an image, or an unsupported format
                throw new IOException("Could not decode image from stream. The image format might not be supported or the stream is invalid/empty.");
            }
            Hash hash = hasher.hash(image);
            return hash.getHashValue().toString(16); // Return hash as a hex string
        } finally {
            // InputStream is managed by try-with-resources in the filePath version
            // For direct InputStream version, the caller should manage the stream if it needs to be closed by them.
            // However, if this method is always called with streams that *it* should close, then:
            try {
                imageStream.close();
            } catch (IOException e) {
                logger.warn("Failed to close image stream: {}", e.getMessage(), e);
            }
        }
    }

    public double calculateSimilarity(String fingerprint1Hex, String fingerprint2Hex) {
        if (fingerprint1Hex == null || fingerprint1Hex.isEmpty()) {
            throw new IllegalArgumentException("Fingerprint 1 cannot be null or empty.");
        }
        if (fingerprint2Hex == null || fingerprint2Hex.isEmpty()) {
            throw new IllegalArgumentException("Fingerprint 2 cannot be null or empty.");
        }

        try {
            // The jimagehash library expects Hash objects for comparison.
            // We need to reconstruct them from the hex strings.
            // The library internally handles the conversion from BigInteger to its internal hash representation.
            Hash hash1 = new Hash(new java.math.BigInteger(fingerprint1Hex, 16), 0, hasher.getKeyResolution()); // Assuming keyResolution is what's needed for length
            Hash hash2 = new Hash(new java.math.BigInteger(fingerprint2Hex, 16), 0, hasher.getKeyResolution());

            // Normalized Hamming distance: 0.0 means identical, 1.0 means completely different.
            // We want similarity, so 1.0 - normalizedDistance.
            double normalizedDistance = hash1.normalizedHammingDistance(hash2);
            return 1.0 - normalizedDistance;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid fingerprint format. Fingerprints must be valid hexadecimal strings.", e);
        }
    }
}
