-- Penghapusan lunak: dokumen yang dihapus pengguna disembunyikan, baris dan jejak audit tetap ada.
ALTER TABLE ocr_documents ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
