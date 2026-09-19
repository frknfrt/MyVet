-- Kat 0: consent_records.tenant_id, owners.tenant_id'den (owner_id uzerinden) geriye doldurulur.
ALTER TABLE consent_records ADD COLUMN tenant_id UUID;
UPDATE consent_records t SET tenant_id = o.tenant_id FROM owners o WHERE t.owner_id = o.id;
ALTER TABLE consent_records ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_consent_records_tenant_id ON consent_records (tenant_id);
