package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Body callback ms-ocr ke API (api-spec bagian 8). Bentuk sukses atau gagal dibedakan oleh `status`. */
public class ExtractionCallbackRequest {

    @JsonProperty("status")
    public String status; // "extracted" | "failed"

    @JsonProperty("doc_type")
    public String docType;

    @JsonProperty("ocr_source")
    public String ocrSource;

    @JsonProperty("extraction_path")
    public String extractionPath;

    @JsonProperty("schema_version")
    public String schemaVersion;

    @JsonProperty("ocr_text")
    public String ocrText;

    @JsonProperty("fields")
    public Map<String, Object> fields;

    /** Pengayaan: pendamping vision melengkapi dokumen yang sudah COMPLETED. Hanya field kosong (atau bertanda replace). */
    @JsonProperty("enrichment")
    public boolean enrichment;

    @JsonProperty("error_code")
    public String errorCode;

    @JsonProperty("error_message")
    public String errorMessage;
}
