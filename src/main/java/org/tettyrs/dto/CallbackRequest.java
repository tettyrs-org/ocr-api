package org.tettyrs.dto;

import java.util.UUID;

public class CallbackRequest {
    public UUID documentId;
    public String webhookUrl;
    public String eventType;
    public String payload;
}
