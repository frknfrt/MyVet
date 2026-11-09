CREATE TABLE clinic_privacy_notices (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id),
    version BIGINT NOT NULL CHECK (version > 0),
    notice_text TEXT NOT NULL,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE privacy_notice_deliveries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    owner_id UUID NOT NULL REFERENCES owners(id),
    notice_version BIGINT NOT NULL,
    notice_hash VARCHAR(64) NOT NULL,
    notice_text TEXT NOT NULL,
    recorded_by UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    client_ip TEXT
);
CREATE INDEX ix_privacy_notice_deliveries_owner ON privacy_notice_deliveries (tenant_id, owner_id, recorded_at DESC);

ALTER TABLE owners ADD COLUMN email_notification_consent BOOLEAN NOT NULL DEFAULT FALSE;
