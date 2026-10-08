-- ============================================
-- Migrate Document ID from BIGSERIAL to UUID
-- ============================================

-- Drop all dependent foreign key constraints first
ALTER TABLE ocr_audit_events DROP CONSTRAINT IF EXISTS ocr_audit_events_document_id_fkey;
ALTER TABLE ocr_audit_events DROP CONSTRAINT IF EXISTS fk_ocr_audit_document;
ALTER TABLE ocr_travel_assignments DROP CONSTRAINT IF EXISTS ocr_travel_assignments_document_id_fkey;
ALTER TABLE ocr_travel_assignments DROP CONSTRAINT IF EXISTS fk_ocr_assign_document;
ALTER TABLE ocr_document_processing_results DROP CONSTRAINT IF EXISTS ocr_document_processing_results_document_id_fkey;
ALTER TABLE ocr_document_verifications DROP CONSTRAINT IF EXISTS ocr_document_verifications_document_id_fkey;
ALTER TABLE ocr_document_verifications DROP CONSTRAINT IF EXISTS fk_verify_document;
ALTER TABLE ocr_extraction_callbacks DROP CONSTRAINT IF EXISTS ocr_extraction_callbacks_document_id_fkey;
ALTER TABLE ocr_extraction_callbacks DROP CONSTRAINT IF EXISTS fk_callback_document;
ALTER TABLE ocr_telemetry_timings DROP CONSTRAINT IF EXISTS ocr_telemetry_timings_document_id_fkey;
ALTER TABLE ocr_telemetry_timings DROP CONSTRAINT IF EXISTS fk_telemetry_document;

-- Rename existing id column to old_id
ALTER TABLE ocr_documents RENAME COLUMN id TO id_old;

-- Add new UUID column
ALTER TABLE ocr_documents ADD COLUMN id UUID DEFAULT gen_random_uuid();

-- Update the column to not allow null
ALTER TABLE ocr_documents ALTER COLUMN id SET NOT NULL;

-- Set id as primary key
ALTER TABLE ocr_documents DROP CONSTRAINT ocr_documents_pkey;
ALTER TABLE ocr_documents ADD PRIMARY KEY (id);

-- Drop the old_id column
ALTER TABLE ocr_documents DROP COLUMN id_old;

-- ============================================
-- Update dependent tables with UUID references
-- ============================================

-- Update ocr_audit_events
ALTER TABLE ocr_audit_events ALTER COLUMN document_id TYPE UUID USING gen_random_uuid();
ALTER TABLE ocr_audit_events ADD CONSTRAINT fk_ocr_audit_document
    FOREIGN KEY (document_id) REFERENCES ocr_documents(id) ON DELETE CASCADE;

-- Update ocr_travel_assignments
ALTER TABLE ocr_travel_assignments ALTER COLUMN document_id TYPE UUID USING gen_random_uuid();
ALTER TABLE ocr_travel_assignments ADD CONSTRAINT fk_ocr_assign_document
    FOREIGN KEY (document_id) REFERENCES ocr_documents(id) ON DELETE CASCADE;

-- Update ocr_document_processing_results
ALTER TABLE ocr_document_processing_results ALTER COLUMN document_id TYPE UUID USING gen_random_uuid();
ALTER TABLE ocr_document_processing_results ADD CONSTRAINT ocr_document_processing_results_document_id_fkey
    FOREIGN KEY (document_id) REFERENCES ocr_documents(id) ON DELETE CASCADE;

-- Update ocr_document_verifications
ALTER TABLE ocr_document_verifications ALTER COLUMN document_id TYPE UUID USING gen_random_uuid();
ALTER TABLE ocr_document_verifications ADD CONSTRAINT fk_verify_document
    FOREIGN KEY (document_id) REFERENCES ocr_documents(id) ON DELETE CASCADE;

-- Update ocr_extraction_callbacks
ALTER TABLE ocr_extraction_callbacks ALTER COLUMN document_id TYPE UUID USING gen_random_uuid();
ALTER TABLE ocr_extraction_callbacks ADD CONSTRAINT fk_callback_document
    FOREIGN KEY (document_id) REFERENCES ocr_documents(id) ON DELETE CASCADE;

-- Update ocr_telemetry_timings
ALTER TABLE ocr_telemetry_timings ALTER COLUMN document_id TYPE UUID USING gen_random_uuid();
ALTER TABLE ocr_telemetry_timings ADD CONSTRAINT fk_telemetry_document
    FOREIGN KEY (document_id) REFERENCES ocr_documents(id) ON DELETE CASCADE;

-- Update processing_result_id column in ocr_documents (if exists)
-- This is a reference to DocumentProcessingResult.id, not document_id, so we skip it

-- Drop existing indexes if they exist (to handle idempotency)
DROP INDEX IF EXISTS idx_ocr_status;
DROP INDEX IF EXISTS idx_ocr_document_type;
DROP INDEX IF EXISTS idx_ocr_created_by;
DROP INDEX IF EXISTS idx_ocr_created_at;
DROP INDEX IF EXISTS idx_ocr_audit_document_id;
DROP INDEX IF EXISTS idx_ocr_audit_action;
DROP INDEX IF EXISTS idx_ocr_audit_timestamp;
DROP INDEX IF EXISTS idx_ocr_audit_actor;
DROP INDEX IF EXISTS idx_ocr_assign_document_id;
DROP INDEX IF EXISTS idx_ocr_assign_assigned_to;
DROP INDEX IF EXISTS idx_ocr_assign_status;
DROP INDEX IF EXISTS idx_ocr_assign_created_at;
DROP INDEX IF EXISTS idx_processing_document;
DROP INDEX IF EXISTS idx_processing_status;
DROP INDEX IF EXISTS idx_verify_document_id;
DROP INDEX IF EXISTS idx_verify_status;
DROP INDEX IF EXISTS idx_verify_created_at;
DROP INDEX IF EXISTS idx_callback_document_id;
DROP INDEX IF EXISTS idx_callback_status;
DROP INDEX IF EXISTS idx_callback_event_type;
DROP INDEX IF EXISTS idx_callback_created_at;
DROP INDEX IF EXISTS idx_telemetry_document_id;
DROP INDEX IF EXISTS idx_telemetry_component;
DROP INDEX IF EXISTS idx_telemetry_created_at;

-- Recreate indexes
CREATE INDEX idx_ocr_status ON ocr_documents(status);
CREATE INDEX idx_ocr_document_type ON ocr_documents(document_type);
CREATE INDEX idx_ocr_created_by ON ocr_documents(created_by);
CREATE INDEX idx_ocr_created_at ON ocr_documents(created_at);
CREATE INDEX idx_ocr_audit_document_id ON ocr_audit_events(document_id);
CREATE INDEX idx_ocr_audit_action ON ocr_audit_events(action);
CREATE INDEX idx_ocr_audit_timestamp ON ocr_audit_events(timestamp);
CREATE INDEX idx_ocr_audit_actor ON ocr_audit_events(actor);
CREATE INDEX idx_ocr_assign_document_id ON ocr_travel_assignments(document_id);
CREATE INDEX idx_ocr_assign_assigned_to ON ocr_travel_assignments(assigned_to);
CREATE INDEX idx_ocr_assign_status ON ocr_travel_assignments(status);
CREATE INDEX idx_ocr_assign_created_at ON ocr_travel_assignments(created_at);
CREATE INDEX idx_processing_document ON ocr_document_processing_results(document_id);
CREATE INDEX idx_processing_status ON ocr_document_processing_results(processing_status);
CREATE INDEX idx_verify_document_id ON ocr_document_verifications(document_id);
CREATE INDEX idx_verify_status ON ocr_document_verifications(verification_status);
CREATE INDEX idx_verify_created_at ON ocr_document_verifications(created_at);
CREATE INDEX idx_callback_document_id ON ocr_extraction_callbacks(document_id);
CREATE INDEX idx_callback_status ON ocr_extraction_callbacks(callback_status);
CREATE INDEX idx_callback_event_type ON ocr_extraction_callbacks(event_type);
CREATE INDEX idx_callback_created_at ON ocr_extraction_callbacks(created_at);
CREATE INDEX idx_telemetry_document_id ON ocr_telemetry_timings(document_id);
CREATE INDEX idx_telemetry_component ON ocr_telemetry_timings(component);
CREATE INDEX idx_telemetry_created_at ON ocr_telemetry_timings(created_at);
