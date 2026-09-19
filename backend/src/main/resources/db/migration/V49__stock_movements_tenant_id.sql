-- Kat 1: stock_movements.tenant_id, inventory_items.tenant_id'den.
-- inventory_items.tenant_id V39'da dolduruldu.
ALTER TABLE stock_movements ADD COLUMN tenant_id UUID;
UPDATE stock_movements t SET tenant_id = i.tenant_id FROM inventory_items i WHERE t.inventory_item_id = i.id;
ALTER TABLE stock_movements ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_stock_movements_tenant_id ON stock_movements (tenant_id);
