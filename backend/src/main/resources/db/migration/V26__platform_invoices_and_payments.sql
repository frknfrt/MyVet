CREATE TABLE platform_invoices (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL,
    plan_code     TEXT NOT NULL,
    amount        NUMERIC(10,2) NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    due_date      DATE NOT NULL,
    status        TEXT NOT NULL,
    issued_at     TIMESTAMPTZ NOT NULL,
    paid_at       TIMESTAMPTZ
);
CREATE INDEX idx_platform_invoices_tenant_id ON platform_invoices (tenant_id);
CREATE INDEX idx_platform_invoices_status_due_date ON platform_invoices (status, due_date);

CREATE TABLE platform_payments (
    id                    UUID PRIMARY KEY,
    invoice_id            UUID NOT NULL REFERENCES platform_invoices (id),
    amount                NUMERIC(10,2) NOT NULL,
    method                TEXT NOT NULL,
    paid_at               DATE NOT NULL,
    recorded_by_admin_id  UUID NOT NULL,
    notes                 TEXT
);
CREATE INDEX idx_platform_payments_invoice_id ON platform_payments (invoice_id);
