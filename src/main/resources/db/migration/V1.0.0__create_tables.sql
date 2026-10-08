-- Create documents table
CREATE TABLE ocr_documents (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255),
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    s3_path VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    document_type VARCHAR(50) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes untuk documents table
CREATE INDEX idx_ocr_status ON ocr_documents(status);
CREATE INDEX idx_ocr_document_type ON ocr_documents(document_type);
CREATE INDEX idx_ocr_created_by ON ocr_documents(created_by);
CREATE INDEX idx_ocr_created_at ON ocr_documents(created_at);

-- Create audit_events table
CREATE TABLE ocr_audit_events (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES ocr_documents(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    details TEXT,
    actor VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

-- Create indexes untuk audit_events table
CREATE INDEX idx_ocr_audit_document_id ON ocr_audit_events(document_id);
CREATE INDEX idx_ocr_audit_action ON ocr_audit_events(action);
CREATE INDEX idx_ocr_audit_timestamp ON ocr_audit_events(timestamp);
CREATE INDEX idx_ocr_audit_actor ON ocr_audit_events(actor);

-- Create travel_assignments table
CREATE TABLE ocr_travel_assignments (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL UNIQUE REFERENCES ocr_documents(id) ON DELETE CASCADE,
    assigned_to VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create indexes untuk travel_assignments table
CREATE INDEX idx_ocr_assign_document_id ON ocr_travel_assignments(document_id);
CREATE INDEX idx_ocr_assign_assigned_to ON ocr_travel_assignments(assigned_to);
CREATE INDEX idx_ocr_assign_status ON ocr_travel_assignments(status);
CREATE INDEX idx_ocr_assign_created_at ON ocr_travel_assignments(created_at);

-- Create flyway schema history table
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INTEGER NOT NULL,
    version VARCHAR(50),
    description VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    PRIMARY KEY(installed_rank)
);
