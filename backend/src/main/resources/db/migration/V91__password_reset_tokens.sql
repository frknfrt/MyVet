-- "Şifremi unuttum" (2026-11-04): e-postayla gönderilen tek kullanımlık sıfırlama bağlantısı.
-- Bağlantıdaki token saklanmaz, yalnız SHA-256 özeti tutulur; veritabanı sızsa da bağlantı kullanılamaz.
CREATE TABLE password_reset_tokens (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    staff_user_id  UUID        NOT NULL REFERENCES staff_users (id),
    token_hash     VARCHAR(64) NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    used_at        TIMESTAMPTZ
);
CREATE INDEX idx_password_reset_tokens_staff_user_id ON password_reset_tokens (staff_user_id);
