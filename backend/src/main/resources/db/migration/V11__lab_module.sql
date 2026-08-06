CREATE TABLE lab_results (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    patient_id         UUID NOT NULL REFERENCES patients (id),
    ordering_staff_id  UUID REFERENCES staff_users (id),
    test_name          TEXT NOT NULL,
    status             TEXT NOT NULL,
    requested_at       TIMESTAMPTZ NOT NULL,
    resulted_at        TIMESTAMPTZ,
    result_summary     TEXT,
    notes              TEXT
);
CREATE INDEX idx_lab_results_tenant_id ON lab_results (tenant_id);
CREATE INDEX idx_lab_results_patient_id ON lab_results (patient_id);

CREATE TABLE lab_result_items (
    id               UUID PRIMARY KEY,
    lab_result_id    UUID NOT NULL REFERENCES lab_results (id),
    parameter_name   TEXT NOT NULL,
    value            TEXT NOT NULL,
    unit             TEXT,
    reference_range  TEXT,
    flag             TEXT
);
CREATE INDEX idx_lab_result_items_lab_result_id ON lab_result_items (lab_result_id);

CREATE TABLE lab_result_files (
    id             UUID PRIMARY KEY,
    lab_result_id  UUID NOT NULL REFERENCES lab_results (id),
    file_name      TEXT NOT NULL,
    content_type   TEXT,
    file_size      BIGINT NOT NULL,
    content        BYTEA NOT NULL,
    uploaded_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_lab_result_files_lab_result_id ON lab_result_files (lab_result_id);
