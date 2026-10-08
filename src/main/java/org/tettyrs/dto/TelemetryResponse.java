package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelemetryResponse {

    public Long id;
    public UUID documentId;
    public String component;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public Long durationMs;
    public String status;
    public String errorMessage;
    public String requestPayload;
    public String responsePayload;
    public  LocalDateTime createdAt;
}
