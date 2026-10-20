-- Faz 3b (spec 2026-10-15): klinik ici hassas islemlerin degistirilemez kaydi.
-- Platform admin islemleri ayri tabloda kalir (audit_log_entries).
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    actor_type      VARCHAR(16)  NOT NULL,
    actor_id        UUID,
    actor_name      TEXT,
    actor_role      VARCHAR(32),
    impersonated_by TEXT,
    channel         VARCHAR(16)  NOT NULL,
    action          VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(32),
    target_id       UUID,
    target_label    TEXT,
    summary         TEXT,
    ip_address      TEXT,
    request_id      TEXT
);
CREATE INDEX idx_audit_events_tenant_time ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_events_tenant_action_time ON audit_events (tenant_id, action, occurred_at DESC);

-- Kaza / uygulama hatasina karsi: kayitlar guncellenemez ve silinemez.
CREATE FUNCTION audit_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events kayitlari degistirilemez';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();
