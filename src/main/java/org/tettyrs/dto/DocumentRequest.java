package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tettyrs.entities.enums.DocumentType;

@Data
@NoArgsConstructor
public class DocumentRequest {

    @JsonProperty("filename")
    public String filename;

    @JsonProperty("file_size")
    public Long fileSize;

    @JsonProperty("mime_type")
    public String mimeType;

    @JsonProperty("document_type")
    public DocumentType documentType;

    @JsonProperty("created_by")
    public String createdBy;
}
