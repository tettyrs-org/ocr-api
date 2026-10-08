-- ============================================
-- Document Verification Table
-- ============================================
CREATE TABLE IF NOT EXISTS ocr_document_verifications (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    verification_status VARCHAR(50) NOT NULL,
    verification_details TEXT,
    confidence_score FLOAT,
    verified_by VARCHAR(255),
    verified_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_verify_document FOREIGN KEY (document_id)
        REFERENCES ocr_documents(id) ON DELETE CASCADE
);

CREATE INDEX idx_verify_document_id ON ocr_document_verifications(document_id);
CREATE INDEX idx_verify_status ON ocr_document_verifications(verification_status);
CREATE INDEX idx_verify_created_at ON ocr_document_verifications(created_at);

-- ============================================
-- Telemetry Timing Table
-- ============================================
CREATE TABLE IF NOT EXISTS ocr_telemetry_timings (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    component VARCHAR(100) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    request_payload TEXT,
    response_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_telemetry_document FOREIGN KEY (document_id)
        REFERENCES ocr_documents(id) ON DELETE CASCADE
);

CREATE INDEX idx_telemetry_document_id ON ocr_telemetry_timings(document_id);
CREATE INDEX idx_telemetry_component ON ocr_telemetry_timings(component);
CREATE INDEX idx_telemetry_created_at ON ocr_telemetry_timings(created_at);

-- ============================================
-- Extraction Callback Table
-- ============================================
CREATE TABLE IF NOT EXISTS ocr_extraction_callbacks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    webhook_url VARCHAR(2048) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    callback_status VARCHAR(50) NOT NULL,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    last_retry_at TIMESTAMP,
    last_response_code INTEGER,
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_callback_document FOREIGN KEY (document_id)
        REFERENCES ocr_documents(id) ON DELETE CASCADE
);

CREATE INDEX idx_callback_document_id ON ocr_extraction_callbacks(document_id);
CREATE INDEX idx_callback_status ON ocr_extraction_callbacks(callback_status);
CREATE INDEX idx_callback_event_type ON ocr_extraction_callbacks(event_type);
CREATE INDEX idx_callback_created_at ON ocr_extraction_callbacks(created_at);
