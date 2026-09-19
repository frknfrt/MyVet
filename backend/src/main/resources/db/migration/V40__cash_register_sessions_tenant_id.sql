-- Kat 0: cash_register_sessions.tenant_id, branches.tenant_id'den (branch_id uzerinden).
ALTER TABLE cash_register_sessions ADD COLUMN tenant_id UUID;
UPDATE cash_register_sessions t SET tenant_id = b.tenant_id FROM branches b WHERE t.branch_id = b.id;
ALTER TABLE cash_register_sessions ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_cash_register_sessions_tenant_id ON cash_register_sessions (tenant_id);
