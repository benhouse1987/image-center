package com.example.imagefingerprint;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.AverageHash;
import dev.brachtendorf.jimagehash.hashAlgorithms.HashingAlgorithm;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Service
public class ImageService {

    private final HashingAlgorithm hasher = new AverageHash(64); // Using AverageHash with a 64-bit hash

    public String calculateFingerprint(InputStream imageStream) throws IOException {
        if (imageStream == null) {
            throw new IllegalArgumentException("Image stream cannot be null.");
        }
        try {
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                throw new IOException("Could not decode image from stream. The image format might not be supported or the stream is invalid.");
            }
            Hash hash = hasher.hash(image);
            return hash.getHashValue().toString(16); // Return hash as a hex string
        } finally {
            if (imageStream != null) {
                try {
                    imageStream.close();
                } catch (IOException e) {
                    // Log this or handle more gracefully if necessary
                    System.err.println("Failed to close image stream: " + e.getMessage());
                }
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
