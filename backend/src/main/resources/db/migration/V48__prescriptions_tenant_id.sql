-- Kat 1: prescriptions.tenant_id, patients.tenant_id'den. Prescription zaten
-- patient_id'yi DOGRUDAN tasiyor -- Encounter ara adimina gerek yok (spec S4).
ALTER TABLE prescriptions ADD COLUMN tenant_id UUID;
UPDATE prescriptions t SET tenant_id = p.tenant_id FROM patients p WHERE t.patient_id = p.id;
ALTER TABLE prescriptions ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_prescriptions_tenant_id ON prescriptions (tenant_id);
