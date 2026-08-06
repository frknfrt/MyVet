CREATE TABLE notification_log (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    owner_id            UUID NOT NULL REFERENCES owners (id),
    patient_id          UUID REFERENCES patients (id),
    channel             TEXT NOT NULL,
    notification_type   TEXT NOT NULL,
    recipient_contact   TEXT NOT NULL,
    message             TEXT NOT NULL,
    status              TEXT NOT NULL,
    related_entity_id   UUID,
    attempted_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_log_tenant_id ON notification_log (tenant_id);
CREATE INDEX idx_notification_log_related_entity_id ON notification_log (related_entity_id);
