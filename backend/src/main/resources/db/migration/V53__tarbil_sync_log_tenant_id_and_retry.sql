-- tenant_id, patients.tenant_id uzerinden geriye doldurulur (bkz.
-- docs/superpowers/specs/2026-09-19-arka-plan-is-guvenilirligi-genelleme-design.md S4).
-- TarbilSyncLog Hibernate @TenantId DISINDA kalmaya devam ediyor (NotificationLog
-- ile ayni karar) -- bu duz bir sutun, manuel kontrol icin.
ALTER TABLE tarbil_sync_log ADD COLUMN tenant_id UUID;
UPDATE tarbil_sync_log t SET tenant_id = p.tenant_id FROM patients p WHERE t.patient_id = p.id;
ALTER TABLE tarbil_sync_log ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_tarbil_sync_log_tenant_id ON tarbil_sync_log (tenant_id);

ALTER TABLE tarbil_sync_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE tarbil_sync_log ADD COLUMN next_retry_at TIMESTAMPTZ;
CREATE INDEX idx_tarbil_sync_log_due_retry ON tarbil_sync_log (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
