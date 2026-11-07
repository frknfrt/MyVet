-- Dijital onay formlari. Icerik/snapshot/degerler TEXT (jsonb anahtar sirasini bozar, SHA-256 ozeti kirilir).
CREATE TABLE form_source_images (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    storage_ref   TEXT NOT NULL,
    content_type  TEXT NOT NULL,
    uploaded_by   UUID NOT NULL,
    uploaded_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE form_templates (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    title            TEXT NOT NULL,
    content          TEXT NOT NULL,
    source_image_id  UUID NULL REFERENCES form_source_images (id),
    status           TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_by       UUID NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_form_templates_tenant_status ON form_templates (tenant_id, status);

CREATE TABLE signed_forms (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    template_id           UUID NOT NULL REFERENCES form_templates (id),
    template_title        TEXT NOT NULL,
    template_snapshot     TEXT NOT NULL,
    values_json           TEXT NOT NULL,
    signature_storage_ref TEXT NOT NULL,
    patient_id            UUID NOT NULL,
    owner_id              UUID NOT NULL,
    encounter_id          UUID NULL,
    signer_type           TEXT NOT NULL CHECK (signer_type IN ('OWNER', 'OTHER')),
    signer_name           TEXT NOT NULL,
    signer_national_id    TEXT NULL,
    signer_phone          TEXT NULL,
    signed_by             UUID NOT NULL,
    signed_at             TIMESTAMPTZ NOT NULL,
    client_ip             TEXT NULL,
    user_agent            TEXT NULL,
    content_digest        TEXT NOT NULL,
    status                TEXT NOT NULL CHECK (status IN ('VALID', 'VOIDED')),
    void_reason           TEXT NULL,
    voided_by             UUID NULL,
    voided_at             TIMESTAMPTZ NULL
);
CREATE INDEX idx_signed_forms_tenant_patient ON signed_forms (tenant_id, patient_id);
CREATE INDEX idx_signed_forms_tenant_owner ON signed_forms (tenant_id, owner_id);
