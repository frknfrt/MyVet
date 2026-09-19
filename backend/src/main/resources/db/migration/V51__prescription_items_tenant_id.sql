-- Kat 2: prescription_items.tenant_id, prescriptions.tenant_id'den.
-- prescriptions.tenant_id V48'de dolduruldu.
ALTER TABLE prescription_items ADD COLUMN tenant_id UUID;
UPDATE prescription_items t SET tenant_id = p.tenant_id FROM prescriptions p WHERE t.prescription_id = p.id;
ALTER TABLE prescription_items ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_prescription_items_tenant_id ON prescription_items (tenant_id);
