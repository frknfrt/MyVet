-- TARBIL otomasyon cekirdegi P0 (docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md S5.1).
-- Asiya ozel aktarim kaydi belge turlerine acilir; mevcut satirlar VACCINATION olarak kalir, kimlikleri degismez.
ALTER TABLE tarbil_sync_log RENAME TO tarbil_submission;
ALTER TABLE tarbil_submission RENAME COLUMN sync_type TO document_type;
ALTER TABLE tarbil_submission RENAME COLUMN vaccination_record_id TO source_id;
-- Stok kabulde hasta yok.
ALTER TABLE tarbil_submission ALTER COLUMN patient_id DROP NOT NULL;

DROP INDEX IF EXISTS uq_tarbil_sync_log_vaccination_record_id;
CREATE UNIQUE INDEX uq_tarbil_submission_source ON tarbil_submission (tenant_id, document_type, source_id);

ALTER INDEX IF EXISTS idx_tarbil_sync_log_tenant_status RENAME TO idx_tarbil_submission_tenant_status;
ALTER INDEX IF EXISTS idx_tarbil_sync_log_tenant_id RENAME TO idx_tarbil_submission_tenant_id;
ALTER INDEX IF EXISTS idx_tarbil_sync_log_patient_id RENAME TO idx_tarbil_submission_patient_id;
