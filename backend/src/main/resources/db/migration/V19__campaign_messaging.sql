ALTER TABLE notification_log ALTER COLUMN owner_id DROP NOT NULL;
ALTER TABLE notification_log ADD COLUMN recipient_label TEXT;

CREATE TABLE message_templates (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants (id),
    name        TEXT NOT NULL,
    channel     TEXT NOT NULL,
    category    TEXT NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_message_templates_tenant_id ON message_templates (tenant_id);
