package org.tettyrs.dto;

public class ClassificationResult {
    public String category;
    public Double confidence;
    public String summary;


    public ClassificationResult() {
    }

    public ClassificationResult(String category, Double confidence, String summary) {
        this.category = category;
        this.confidence = confidence;
        this.summary = summary;
    }
}
