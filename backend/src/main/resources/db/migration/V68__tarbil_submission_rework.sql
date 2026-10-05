-- TARBIL eklentisi Faz 1 (bkz. docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md S5.1-5.2).
-- MockTarbilAdapter'in SYNCED/FAILED isaretledigi kayitlarin HICBIRI TARBIL'e gercekten
-- gitmedi -- hepsi "bekliyor"a doner. Kimliklendirme/tedavi kuyruklamasi durduruldu;
-- o turlerdeki satirlar da hic gonderilmedigi icin silinmesi bilgi kaybi degil.
DELETE FROM tarbil_sync_log WHERE sync_type <> 'VACCINATION';
UPDATE tarbil_sync_log SET status = 'PENDING' WHERE status IN ('SYNCED', 'FAILED');

ALTER TABLE tarbil_sync_log ADD COLUMN vaccination_record_id UUID;
UPDATE tarbil_sync_log t
SET vaccination_record_id = v.id
FROM vaccination_records v
WHERE v.patient_id = t.patient_id
  AND t.payload IS NOT NULL
  AND v.vaccine_name = (t.payload::jsonb ->> 'vaccineName')
  AND v.administered_date = (t.payload::jsonb ->> 'administeredDate')::date;
-- Eslestirilemeyen (kaydi silinmis) ve ayni asiya dusen mukerrer satirlar atilir.
DELETE FROM tarbil_sync_log WHERE vaccination_record_id IS NULL;
DELETE FROM tarbil_sync_log a USING tarbil_sync_log b
WHERE a.vaccination_record_id = b.vaccination_record_id AND a.id > b.id;
ALTER TABLE tarbil_sync_log ALTER COLUMN vaccination_record_id SET NOT NULL;
CREATE UNIQUE INDEX uq_tarbil_sync_log_vaccination_record_id ON tarbil_sync_log (vaccination_record_id);

ALTER TABLE tarbil_sync_log RENAME COLUMN attempted_at TO queued_at;
ALTER TABLE tarbil_sync_log ADD COLUMN submitted_at TIMESTAMPTZ;
ALTER TABLE tarbil_sync_log ADD COLUMN submitted_by_staff_id UUID;
ALTER TABLE tarbil_sync_log ADD COLUMN confirmation_method TEXT;
ALTER TABLE tarbil_sync_log ADD COLUMN tarbil_reference TEXT;
ALTER TABLE tarbil_sync_log ADD COLUMN dismissed_reason TEXT;
ALTER TABLE tarbil_sync_log ADD COLUMN dismissed_at TIMESTAMPTZ;
ALTER TABLE tarbil_sync_log ADD COLUMN dismissed_by_staff_id UUID;

DROP INDEX IF EXISTS idx_tarbil_sync_log_due_retry;
ALTER TABLE tarbil_sync_log DROP COLUMN payload;
ALTER TABLE tarbil_sync_log DROP COLUMN attempt_count;
ALTER TABLE tarbil_sync_log DROP COLUMN next_retry_at;
-- V67 (platform admin saglik paneli, eski sunucu-senkron modeli) bu sutunu ekledi; eklenti modelinde kullanilmiyor.
ALTER TABLE tarbil_sync_log DROP COLUMN IF EXISTS failure_reason;
CREATE INDEX idx_tarbil_sync_log_tenant_status ON tarbil_sync_log (tenant_id, status);
