-- Hekime ozel TARBIL eklentisi anahtarlari. Kod ve anahtar yalnizca SHA-256 ozeti olarak durur.
-- @TenantId DISINDA: kimlik dogrulama sirasinda kiraci henuz bilinmiyor (anahtar ozetiyle global arama).
CREATE TABLE tarbil_extension_token (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    staff_user_id      UUID NOT NULL,
    label              TEXT,
    token_hash         TEXT UNIQUE,
    pairing_code_hash  TEXT UNIQUE,
    pairing_expires_at TIMESTAMPTZ NOT NULL,
    paired_at          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    last_used_at       TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ
);
CREATE INDEX idx_tarbil_extension_token_tenant ON tarbil_extension_token (tenant_id);
