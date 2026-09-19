-- Kat 1: encounters.tenant_id, patients.tenant_id'den (patient_id uzerinden).
-- patients.tenant_id V36'da dolduruldu -- bu migration ondan SONRA calismali.
ALTER TABLE encounters ADD COLUMN tenant_id UUID;
UPDATE encounters t SET tenant_id = p.tenant_id FROM patients p WHERE t.patient_id = p.id;
ALTER TABLE encounters ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_encounters_tenant_id ON encounters (tenant_id);
