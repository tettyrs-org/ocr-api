package org.tettyrs.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.tettyrs.dto.OcrResult;

@ApplicationScoped
public class OcrService {

    public OcrResult extractText(String filename, byte[] fileContent) {
        try {
            // Mock OCR: simulate processing delay
            Thread.sleep(500);

            // Mock result based on filename
            String mockText = "Extracted text from " + filename +
                    "\nDocument appears to be " +
                    "a professional document with structured content.";

            return new OcrResult(
                    mockText,
                    0.95,  // confidence
                    "en"   // language
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OCR processing interrupted", e);
        }
    }
}
