package org.tettyrs.dto;

public class CallbackRequest {
    public Long documentId;
    public String webhookUrl;
    public String eventType;
    public String payload;
}
