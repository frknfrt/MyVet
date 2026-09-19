-- Kat 0: lab_result_files.tenant_id, lab_results.tenant_id'den.
ALTER TABLE lab_result_files ADD COLUMN tenant_id UUID;
UPDATE lab_result_files t SET tenant_id = r.tenant_id FROM lab_results r WHERE t.lab_result_id = r.id;
ALTER TABLE lab_result_files ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_lab_result_files_tenant_id ON lab_result_files (tenant_id);
