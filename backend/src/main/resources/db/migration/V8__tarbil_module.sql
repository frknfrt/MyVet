CREATE TABLE tarbil_sync_log (
    id            UUID PRIMARY KEY,
    patient_id    UUID NOT NULL REFERENCES patients (id),
    sync_type     TEXT NOT NULL,
    status        TEXT NOT NULL,
    payload       TEXT,
    attempted_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_tarbil_sync_log_patient_id ON tarbil_sync_log (patient_id);
