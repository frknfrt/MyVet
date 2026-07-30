CREATE TABLE species (
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE breeds (
    id         UUID PRIMARY KEY,
    species_id UUID NOT NULL REFERENCES species (id),
    name       TEXT NOT NULL
);
CREATE INDEX idx_breeds_species_id ON breeds (species_id);

CREATE TABLE owners (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    full_name           TEXT NOT NULL,
    phone               TEXT NOT NULL,
    email               TEXT,
    national_id_masked  TEXT,
    address             TEXT,
    marketing_consent   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_owners_tenant_id ON owners (tenant_id);

CREATE TABLE patients (
    id               UUID PRIMARY KEY,
    owner_id         UUID NOT NULL REFERENCES owners (id),
    species_id       UUID NOT NULL REFERENCES species (id),
    breed_id         UUID REFERENCES breeds (id),
    name             TEXT NOT NULL,
    sex              TEXT,
    neutered         BOOLEAN NOT NULL DEFAULT FALSE,
    birth_date       DATE,
    microchip_no     TEXT,
    tarbil_animal_id TEXT,
    weight_kg        NUMERIC(6, 2),
    photo_url        TEXT,
    status           TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_patients_owner_id ON patients (owner_id);

CREATE TABLE consent_records (
    id           UUID PRIMARY KEY,
    owner_id     UUID NOT NULL REFERENCES owners (id),
    consent_type TEXT NOT NULL,
    granted      BOOLEAN NOT NULL,
    ip_address   TEXT,
    granted_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX idx_consent_records_owner_id ON consent_records (owner_id);

INSERT INTO species (id, name) VALUES
    (gen_random_uuid(), 'Kedi'),
    (gen_random_uuid(), 'Kopek'),
    (gen_random_uuid(), 'Sigir'),
    (gen_random_uuid(), 'Kanatli'),
    (gen_random_uuid(), 'Egzotik');
