-- Unggahan otomatis (kategori auto): jenis dikenali dari isi berkas, jadi kategori belum tentu identity saat
-- diunggah. Status enkripsi berkas disimpan terpisah dari kategori, supaya tidak berubah bila kategori berubah.
-- Dokumen identity yang sudah ada sudah dienkripsi sejak awal, jadi ditandai sesuai kondisi itu.
ALTER TABLE ocr_documents ADD COLUMN IF NOT EXISTS stored_encrypted BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE ocr_documents SET stored_encrypted = TRUE WHERE category = 'identity';
