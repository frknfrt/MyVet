CREATE TABLE drug_catalog (
    id                UUID PRIMARY KEY,
    name              TEXT NOT NULL,
    active_ingredient TEXT,
    is_controlled     BOOLEAN NOT NULL DEFAULT FALSE,
    interaction_flags TEXT
);

CREATE TABLE encounters (
    id                UUID PRIMARY KEY,
    patient_id        UUID NOT NULL REFERENCES patients (id),
    staff_user_id     UUID NOT NULL REFERENCES staff_users (id),
    appointment_id    UUID REFERENCES appointments (id),
    encounter_date    TIMESTAMPTZ NOT NULL,
    subjective        TEXT,
    objective         TEXT,
    assessment        TEXT,
    plan              TEXT,
    weight_kg         NUMERIC(6, 2),
    temperature_c     NUMERIC(4, 1),
    heart_rate        INT,
    respiratory_rate  INT,
    template_used     TEXT,
    status            TEXT NOT NULL,
    ai_generated      BOOLEAN NOT NULL DEFAULT FALSE,
    finalized_at      TIMESTAMPTZ
);
CREATE INDEX idx_encounters_patient_id ON encounters (patient_id);

CREATE TABLE vaccination_records (
    id                        UUID PRIMARY KEY,
    patient_id                UUID NOT NULL REFERENCES patients (id),
    encounter_id              UUID REFERENCES encounters (id),
    vaccine_name              TEXT NOT NULL,
    lot_number                TEXT,
    administered_date         DATE NOT NULL,
    next_due_date             DATE,
    administered_by_staff_id  UUID NOT NULL REFERENCES staff_users (id),
    reminder_sent             BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_vaccination_records_patient_id ON vaccination_records (patient_id);

CREATE TABLE prescriptions (
    id                        UUID PRIMARY KEY,
    patient_id                UUID NOT NULL REFERENCES patients (id),
    encounter_id              UUID NOT NULL REFERENCES encounters (id),
    prescribing_staff_id      UUID NOT NULL REFERENCES staff_users (id),
    issued_date               DATE NOT NULL,
    status                    TEXT NOT NULL,
    controlled_substance      BOOLEAN NOT NULL DEFAULT FALSE,
    pharmacy_integration_ref  TEXT
);
CREATE INDEX idx_prescriptions_patient_id ON prescriptions (patient_id);

CREATE TABLE prescription_items (
    id              UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES prescriptions (id),
    drug_id         UUID NOT NULL REFERENCES drug_catalog (id),
    dosage          TEXT NOT NULL,
    frequency       TEXT NOT NULL,
    duration_days   INT NOT NULL,
    route           TEXT NOT NULL
);
CREATE INDEX idx_prescription_items_prescription_id ON prescription_items (prescription_id);

INSERT INTO drug_catalog (id, name, active_ingredient, is_controlled) VALUES
    (gen_random_uuid(), 'Amoksisilin', 'Amoxicillin', FALSE),
    (gen_random_uuid(), 'Meloksikam', 'Meloxicam', FALSE),
    (gen_random_uuid(), 'Ketamin', 'Ketamine', TRUE);
