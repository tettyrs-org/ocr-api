-- Undangan pegawai: form sekali pakai (api-spec.md bagian 16). Hanya hash token yang disimpan.
CREATE TABLE IF NOT EXISTS ocr_invitations (
    id          BIGSERIAL PRIMARY KEY,
    uid         TEXT NOT NULL REFERENCES ocr_users(uid) ON DELETE CASCADE,
    token_hash  CHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_invitations_uid ON ocr_invitations (uid);
