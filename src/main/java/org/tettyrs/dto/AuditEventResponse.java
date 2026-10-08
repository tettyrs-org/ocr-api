package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tettyrs.entities.enums.AuditAction;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class AuditEventResponse {

    @JsonProperty("id")
    public Long id;

    @JsonProperty("action")
    public AuditAction action;

    @JsonProperty("details")
    public String details;

    @JsonProperty("actor")
    public String actor;

    @JsonProperty("timestamp")
    public LocalDateTime timestamp;
}
