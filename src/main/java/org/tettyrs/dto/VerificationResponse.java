package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerificationResponse {

    public Long id;
    public Long documentId;
    public String verificationStatus;
    public String verificationDetails;
    public Float confidenceScore;
    public String verifiedBy;
    public LocalDateTime verifiedAt;
    public String notes;
    public LocalDateTime createdAt;
    public DocumentInfo document;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentInfo {
        public Long id;
        public String filename;
        public String documentType;
        public LocalDateTime createdAt;
    }
}
