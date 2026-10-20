-- Field hasil ekstraksi (api-spec bagian 8). Disimpan sebagai JSON teks. Untuk identitas diri,
-- isinya dienkripsi oleh API sebelum disimpan (database-schema.md, PRD bagian 12 OQ-8).
ALTER TABLE ocr_documents ADD COLUMN extracted_json TEXT;
