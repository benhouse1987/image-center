package com.example.imagefingerprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);
    private final ImageService imageService;

    @Autowired
    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/fingerprint")
    public ResponseEntity<?> calculateFingerprint(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            logger.warn("Upload attempt with an empty file.");
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is empty."));
        }
        try {
            logger.info("Calculating fingerprint for uploaded file: {}", file.getOriginalFilename());
            String fingerprint = imageService.calculateFingerprint(file.getInputStream());
            return ResponseEntity.ok(Map.of("fingerprint", fingerprint));
        } catch (IOException e) {
            logger.error("Failed to process uploaded image file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process image: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("Illegal argument for fingerprint calculation (upload): {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    static class FilePathRequest {
        private String filePath;

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
    }

    @PostMapping("/fingerprint-local")
    public ResponseEntity<?> calculateFingerprintLocal(@RequestBody FilePathRequest request) {
        String filePath = request.getFilePath();
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("Local fingerprint request with empty file path.");
            return ResponseEntity.badRequest().body(Map.of("error", "File path is empty."));
        }
        try {
            logger.info("Calculating fingerprint for local file: {}", filePath);
            String fingerprint = imageService.calculateFingerprint(filePath);
            return ResponseEntity.ok(Map.of("fingerprint", fingerprint));
        } catch (FileNotFoundException e) {
            logger.warn("Local file not found for fingerprint calculation: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "File not found at path: " + filePath));
        } catch (IOException e) {
            logger.error("Failed to process local image file: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process image: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("Illegal argument for fingerprint calculation (local): {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    static class SimilarityRequest {
        private String fingerprint1;
        private String fingerprint2;

        // Getters and Setters are needed for Jackson deserialization
        public String getFingerprint1() {
            return fingerprint1;
        }

        public void setFingerprint1(String fingerprint1) {
            this.fingerprint1 = fingerprint1;
        }

        public String getFingerprint2() {
            return fingerprint2;
        }

        public void setFingerprint2(String fingerprint2) {
            this.fingerprint2 = fingerprint2;
        }
    }

    @PostMapping("/similarity")
    public ResponseEntity<?> calculateSimilarity(@RequestBody SimilarityRequest request) {
        try {
            if (request.getFingerprint1() == null || request.getFingerprint2() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Both fingerprint1 and fingerprint2 must be provided."));
            }
            double similarity = imageService.calculateSimilarity(request.getFingerprint1(), request.getFingerprint2());
            return ResponseEntity.ok(Map.of("similarity", similarity));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("An unexpected error occurred during similarity calculation.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
}
