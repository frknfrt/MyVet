CREATE TABLE ai_jobs (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL REFERENCES tenants (id),
    task_type                  TEXT NOT NULL,
    encounter_id               UUID NOT NULL REFERENCES encounters (id),
    suggestion_text            TEXT NOT NULL,
    model_name                 TEXT NOT NULL,
    model_version              TEXT NOT NULL,
    requested_by_staff_user_id UUID NOT NULL REFERENCES staff_users (id),
    created_at                 TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ai_jobs_tenant_id ON ai_jobs(tenant_id);
CREATE INDEX idx_ai_jobs_encounter_id ON ai_jobs(encounter_id);

CREATE TABLE ai_job_decisions (
    id                        UUID PRIMARY KEY,
    ai_job_id                 UUID NOT NULL UNIQUE REFERENCES ai_jobs (id),
    decision_status           TEXT,
    applied_content           TEXT,
    decided_by_staff_user_id  UUID REFERENCES staff_users (id),
    decided_at                TIMESTAMPTZ,
    accuracy_feedback         TEXT,
    feedback_at               TIMESTAMPTZ
);
