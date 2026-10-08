package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class ProcessingResult {
    @JsonProperty("ocr_text")
    public String ocrText;

    @JsonProperty("ocr_confidence")
    public Double ocrConfidence;

    @JsonProperty("classification_category")
    public String classificationCategory;

    @JsonProperty("classification_confidence")
    public Double classificationConfidence;

    public String summary;

    @JsonProperty("processing_status")
    public String processingStatus;

    @JsonProperty("error_message")
    public String errorMessage;

    @JsonProperty("completed_at")
    public LocalDateTime completedAt;
}
