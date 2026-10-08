package org.tettyrs.dto;

public class OcrResult {
    public String text;
    public Double confidence;
    public String language;

    public OcrResult() {
    }

    public OcrResult(String text, Double confidence, String language) {
        this.text = text;
        this.confidence = confidence;
        this.language = language;
    }

}




