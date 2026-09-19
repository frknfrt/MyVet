-- Kat 2: encounter_inventory_usage.tenant_id, encounters.tenant_id'den.
-- encounters.tenant_id V47'de dolduruldu -- bu migration ondan SONRA calismali.
ALTER TABLE encounter_inventory_usage ADD COLUMN tenant_id UUID;
UPDATE encounter_inventory_usage t SET tenant_id = e.tenant_id FROM encounters e WHERE t.encounter_id = e.id;
ALTER TABLE encounter_inventory_usage ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_encounter_inventory_usage_tenant_id ON encounter_inventory_usage (tenant_id);
