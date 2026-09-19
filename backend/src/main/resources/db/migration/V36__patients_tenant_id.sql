-- Kat 0: patients.tenant_id, owners.tenant_id'den (owner_id uzerinden) geriye doldurulur.
-- Hibernate @TenantId ile otomatik kiraci filtresi icin -- bkz.
-- docs/superpowers/specs/2026-09-17-kiraci-izolasyonu-sertlestirme-design.md S4.
ALTER TABLE patients ADD COLUMN tenant_id UUID;
UPDATE patients t SET tenant_id = o.tenant_id FROM owners o WHERE t.owner_id = o.id;
ALTER TABLE patients ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_patients_tenant_id ON patients (tenant_id);
