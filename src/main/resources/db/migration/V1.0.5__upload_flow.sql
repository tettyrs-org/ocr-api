-- Alur unggah sesuai api-spec bagian 2 dan database-schema.md bagian 4:
--   doc_ref            : nomor rujukan untuk manusia (DOC-########), unik.
--   category           : kategori dari S4a (identity | reimburse | other).
--   upload_idempotency_key : kunci Idempotency-Key, unik per pengunggah.

ALTER TABLE ocr_documents
    ADD COLUMN doc_ref VARCHAR(12),
    ADD COLUMN category VARCHAR(16),
    ADD COLUMN upload_idempotency_key VARCHAR(64);

-- Dokumen lama mendapat doc_ref turunan dari id-nya (deterministik, tidak bertabrakan dalam praktik).
UPDATE ocr_documents
SET doc_ref = 'DOC-' || lpad(
        ((('x' || substr(md5(id::text), 1, 8))::bit(32)::bigint) % 100000000)::text, 8, '0')
WHERE doc_ref IS NULL;

ALTER TABLE ocr_documents ALTER COLUMN doc_ref SET NOT NULL;

CREATE UNIQUE INDEX uq_ocr_doc_ref ON ocr_documents (doc_ref);

CREATE UNIQUE INDEX uq_ocr_upload_idem ON ocr_documents (created_by, upload_idempotency_key)
    WHERE upload_idempotency_key IS NOT NULL;
