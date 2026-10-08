package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tettyrs.entities.enums.AssignmentStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TravelAssignmentResponse {

    @JsonProperty("id")
    public Long id;

    @JsonProperty("document_id")
    public Long documentId;

    @JsonProperty("assigned_to")
    public String assignedTo;

    @JsonProperty("status")
    public AssignmentStatus status;

    @JsonProperty("notes")
    public String notes;

    @JsonProperty("created_at")
    public LocalDateTime createdAt;

    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;
}
