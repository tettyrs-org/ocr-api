package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.tettyrs.dto.ClassificationResult;

@ApplicationScoped
public class LlmService {

    public ClassificationResult classify(String text, String documentType) {
        try {
            // Mock LLM: simulate processing delay
            Thread.sleep(300);

            // Classify based on document type
            String category = getCategory(documentType);
            String summary = generateSummary(text, category);

            return new ClassificationResult(
                    category,
                    0.92,      // confidence
                    summary
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Classification processing interrupted", e);
        }
    }

    private String getCategory(String documentType) {
        if (documentType == null) return "GENERAL";

        return switch(documentType) {
            case "PASSPORT" -> "IDENTITY";
            case "LICENSE" -> "IDENTITY";
            case "INVOICE" -> "FINANCIAL";
            case "CONTRACT" -> "LEGAL";
            default -> "GENERAL";
        };
    }

    private String generateSummary(String text, String category) {
        return "Document classified as " + category +
                ". Contains " + text.length() + " characters of text content.";
    }
}
