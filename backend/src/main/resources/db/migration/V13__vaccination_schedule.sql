-- Asi Takvimi: mevcut vaccination_records tablosunu hem gecmis (ADMINISTERED)
-- hem de ileri tarihli hatirlatma (SCHEDULED) kayitlarini destekleyecek sekilde genisletir.

ALTER TABLE vaccination_records ADD COLUMN tenant_id UUID;

UPDATE vaccination_records vr
SET tenant_id = o.tenant_id
FROM patients p
JOIN owners o ON p.owner_id = o.id
WHERE vr.patient_id = p.id;

ALTER TABLE vaccination_records ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE vaccination_records ADD CONSTRAINT fk_vaccination_records_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
CREATE INDEX idx_vaccination_records_tenant_id ON vaccination_records (tenant_id);

ALTER TABLE vaccination_records ADD COLUMN status TEXT NOT NULL DEFAULT 'ADMINISTERED';
ALTER TABLE vaccination_records ADD COLUMN notes TEXT;
