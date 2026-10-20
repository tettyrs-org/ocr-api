-- Profil pengguna yang akunnya dibuat admin di Firebase Authentication.
-- Login dan password dikelola Firebase; tabel ini menyimpan profil dan role sebagai salinan
-- dari custom claim `roles`. Kontrak: documents/tech/auth-firebase.md bagian 8.
CREATE TABLE IF NOT EXISTS ocr_users (
    uid                  TEXT PRIMARY KEY,
    email                TEXT NOT NULL UNIQUE,
    roles                TEXT[] NOT NULL,
    must_change_password BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ocr_users_roles_valid CHECK (roles <@ ARRAY['USER', 'ADMIN', 'VIEWER']::TEXT[])
);
