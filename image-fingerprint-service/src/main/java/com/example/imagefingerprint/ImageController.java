package com.example.imagefingerprint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/image")
public class ImageController {

    private final ImageService imageService;

    @Autowired
    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping("/fingerprint")
    public ResponseEntity<?> calculateFingerprint(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is empty."));
        }
        try {
            String fingerprint = imageService.calculateFingerprint(file.getInputStream());
            return ResponseEntity.ok(Map.of("fingerprint", fingerprint));
        } catch (IOException e) {
            // Log the exception server-side for debugging
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process image: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
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
            // Log the exception server-side for debugging
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }
}
