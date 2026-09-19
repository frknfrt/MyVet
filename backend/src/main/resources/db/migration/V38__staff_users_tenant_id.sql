-- Kat 0: staff_users.tenant_id, branches.tenant_id'den (branch_id uzerinden).
ALTER TABLE staff_users ADD COLUMN tenant_id UUID;
UPDATE staff_users t SET tenant_id = b.tenant_id FROM branches b WHERE t.branch_id = b.id;
ALTER TABLE staff_users ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_staff_users_tenant_id ON staff_users (tenant_id);
