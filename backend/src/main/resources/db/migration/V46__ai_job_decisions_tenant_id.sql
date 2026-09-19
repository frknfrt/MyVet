-- Kat 0: ai_job_decisions.tenant_id, ai_jobs.tenant_id'den.
ALTER TABLE ai_job_decisions ADD COLUMN tenant_id UUID;
UPDATE ai_job_decisions t SET tenant_id = j.tenant_id FROM ai_jobs j WHERE t.ai_job_id = j.id;
ALTER TABLE ai_job_decisions ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_ai_job_decisions_tenant_id ON ai_job_decisions (tenant_id);
