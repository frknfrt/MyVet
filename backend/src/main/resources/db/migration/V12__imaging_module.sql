CREATE TABLE imaging_records (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    patient_id         UUID NOT NULL REFERENCES patients (id),
    ordering_staff_id  UUID REFERENCES staff_users (id),
    modality           TEXT NOT NULL,
    body_region        TEXT,
    status             TEXT NOT NULL,
    requested_at       TIMESTAMPTZ NOT NULL,
    resulted_at        TIMESTAMPTZ,
    findings           TEXT,
    notes              TEXT
);
CREATE INDEX idx_imaging_records_tenant_id ON imaging_records (tenant_id);
CREATE INDEX idx_imaging_records_patient_id ON imaging_records (patient_id);

CREATE TABLE imaging_record_files (
    id                 UUID PRIMARY KEY,
    imaging_record_id  UUID NOT NULL REFERENCES imaging_records (id),
    file_name          TEXT NOT NULL,
    content_type       TEXT,
    file_size          BIGINT NOT NULL,
    content            BYTEA NOT NULL,
    uploaded_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_imaging_record_files_imaging_record_id ON imaging_record_files (imaging_record_id);
