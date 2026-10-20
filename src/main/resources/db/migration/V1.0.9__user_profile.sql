-- Profil pengguna (api-spec bagian 12). Pengguna undangan mulai dengan profile_completed = false.
ALTER TABLE ocr_users ADD COLUMN IF NOT EXISTS full_name TEXT;
ALTER TABLE ocr_users ADD COLUMN IF NOT EXISTS nip VARCHAR(18);
ALTER TABLE ocr_users ADD COLUMN IF NOT EXISTS profile_completed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS ocr_users_nip_key ON ocr_users (nip) WHERE nip IS NOT NULL;
-- Akun yang sudah ada sebelum undangan diterapkan dianggap sudah lengkap, supaya tidak terkunci.
UPDATE ocr_users SET profile_completed = TRUE WHERE profile_completed = FALSE;
