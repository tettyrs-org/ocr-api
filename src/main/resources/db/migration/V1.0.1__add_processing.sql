CREATE TABLE ocr_document_processing_results (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES ocr_documents(id) ON DELETE CASCADE,
    ocr_text TEXT,
    ocr_confidence DECIMAL(3,2),
    classification_category VARCHAR(100),
    classification_confidence DECIMAL(3,2),
    summary TEXT,
    processing_started_at TIMESTAMP,
    processing_completed_at TIMESTAMP,
    processing_status VARCHAR(20),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_processing_document ON ocr_document_processing_results(document_id);
CREATE INDEX idx_processing_status ON ocr_document_processing_results(processing_status);

ALTER TABLE ocr_documents ADD COLUMN IF NOT EXISTS processing_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE ocr_documents ADD COLUMN IF NOT EXISTS processing_result_id BIGINT REFERENCES ocr_document_processing_results(id);
