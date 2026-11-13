-- Vitrin sitesinden self-servis deneme kaydi (spec 2026-11-13 S4.1).
-- Sifre ve dogrulama kodu yalnizca hash olarak tutulur; kayit tamamlaninca NULL yapilir.
CREATE TABLE trial_signup_requests (
    id                      UUID PRIMARY KEY,
    full_name               TEXT NOT NULL,
    email                   TEXT NOT NULL,
    phone                   TEXT NOT NULL,
    clinic_name             TEXT NOT NULL,
    city                    TEXT NOT NULL,
    chamber_registration_no TEXT NOT NULL,
    password_hash           TEXT,
    otp_hash                TEXT,
    otp_expires_at          TIMESTAMPTZ,
    otp_attempts            INTEGER NOT NULL DEFAULT 0,
    otp_send_count          INTEGER NOT NULL DEFAULT 0,
    last_otp_sent_at        TIMESTAMPTZ,
    status                  TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    tenant_id               UUID,
    consent_accepted_at     TIMESTAMPTZ NOT NULL,
    consent_ip              TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ
);

CREATE INDEX idx_trial_signup_requests_phone_status ON trial_signup_requests (phone, status);
CREATE INDEX idx_trial_signup_requests_phone_created ON trial_signup_requests (phone, created_at);
CREATE INDEX idx_trial_signup_requests_email ON trial_signup_requests (email);

-- Ayni telefonla ikinci bir deneme TAMAMLANAMAZ: eszamanli iki dogrulama uygulama
-- kontrolunu gecse bile veritabani ikincisini reddeder.
CREATE UNIQUE INDEX uq_trial_signup_requests_completed_phone
    ON trial_signup_requests (phone) WHERE status = 'COMPLETED';
