-- Penghapusan berkas asli setelah dokumen final (D-11). Kolom ini mencatat kapan berkas dihapus.
-- Null berarti berkas masih ada di object storage.
ALTER TABLE ocr_documents ADD COLUMN IF NOT EXISTS file_purged_at TIMESTAMP;
