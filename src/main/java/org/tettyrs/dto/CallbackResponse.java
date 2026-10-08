package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallbackResponse {

    public Long id;
    public UUID documentId;
    public String webhookUrl;
    public String eventType;
    public String payload;
    public String callbackStatus;
    public Integer retryCount;
    public Integer maxRetries;
    public LocalDateTime lastRetryAt;
    public Integer lastResponseCode;
    public String lastErrorMessage;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

}
