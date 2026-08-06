CREATE TABLE efatura_submission (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    invoice_id     UUID NOT NULL REFERENCES invoices (id),
    owner_id       UUID NOT NULL REFERENCES owners (id),
    document_type  TEXT NOT NULL,
    total_amount   NUMERIC(12, 2) NOT NULL,
    tax_amount     NUMERIC(12, 2) NOT NULL,
    status         TEXT NOT NULL,
    gib_reference  TEXT,
    attempted_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_efatura_submission_tenant_id ON efatura_submission (tenant_id);
CREATE INDEX idx_efatura_submission_invoice_id ON efatura_submission (invoice_id);
