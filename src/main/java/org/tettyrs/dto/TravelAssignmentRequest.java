package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tettyrs.entities.enums.AssignmentStatus;

@Data
@NoArgsConstructor
public class TravelAssignmentRequest {

    @JsonProperty("document_id")
    public Long documentId;

    @JsonProperty("assigned_to")
    public String assignedTo;

    @JsonProperty("status")
    public AssignmentStatus status;

    @JsonProperty("notes")
    public String notes;
}
