-- Kat 0: imaging_record_files.tenant_id, imaging_records.tenant_id'den.
ALTER TABLE imaging_record_files ADD COLUMN tenant_id UUID;
UPDATE imaging_record_files t SET tenant_id = r.tenant_id FROM imaging_records r WHERE t.imaging_record_id = r.id;
ALTER TABLE imaging_record_files ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_imaging_record_files_tenant_id ON imaging_record_files (tenant_id);
