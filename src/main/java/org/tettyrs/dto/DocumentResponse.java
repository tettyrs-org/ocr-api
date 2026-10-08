package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tettyrs.entities.enums.DocumentStatus;
import org.tettyrs.entities.enums.DocumentType;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class DocumentResponse {

    @JsonProperty("id")
    public UUID id;

    @JsonProperty("filename")
    public String filename;

    @JsonProperty("file_size")
    public Long fileSize;

    @JsonProperty("mime_type")
    public String mimeType;

    @JsonProperty("s3_path")
    public String s3Path;

    @JsonProperty("status")
    public DocumentStatus status;

    @JsonProperty("document_type")
    public DocumentType documentType;

    @JsonProperty("created_by")
    public String createdBy;

    @JsonProperty("created_at")
    public LocalDateTime createdAt;

    @JsonProperty("updated_at")
    public LocalDateTime updatedAt;
}
