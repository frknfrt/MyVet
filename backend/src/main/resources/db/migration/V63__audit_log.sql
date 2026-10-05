CREATE TABLE audit_log_entries (
    id                    UUID PRIMARY KEY,
    platform_admin_id     UUID NOT NULL,
    platform_admin_email  TEXT NOT NULL,
    action                TEXT NOT NULL,
    target_type           TEXT NOT NULL,
    target_id             UUID,
    details               TEXT,
    created_at            TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_log_entries_created_at ON audit_log_entries (created_at DESC);
