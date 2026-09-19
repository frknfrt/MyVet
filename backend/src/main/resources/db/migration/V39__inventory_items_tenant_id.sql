-- Kat 0: inventory_items.tenant_id, branches.tenant_id'den (branch_id uzerinden).
ALTER TABLE inventory_items ADD COLUMN tenant_id UUID;
UPDATE inventory_items t SET tenant_id = b.tenant_id FROM branches b WHERE t.branch_id = b.id;
ALTER TABLE inventory_items ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_inventory_items_tenant_id ON inventory_items (tenant_id);
