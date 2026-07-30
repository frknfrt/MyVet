CREATE TABLE invoices (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    branch_id      UUID NOT NULL REFERENCES branches (id),
    owner_id       UUID NOT NULL REFERENCES owners (id),
    encounter_id   UUID REFERENCES encounters (id),
    e_invoice_ref  TEXT,
    total_amount   NUMERIC(12, 2) NOT NULL,
    tax_amount     NUMERIC(12, 2) NOT NULL,
    status         TEXT NOT NULL,
    issued_at      TIMESTAMPTZ
);
CREATE INDEX idx_invoices_tenant_id ON invoices (tenant_id);
CREATE INDEX idx_invoices_owner_id ON invoices (owner_id);

CREATE TABLE invoice_lines (
    id                 UUID PRIMARY KEY,
    invoice_id         UUID NOT NULL REFERENCES invoices (id),
    description        TEXT NOT NULL,
    quantity           INT NOT NULL,
    unit_price         NUMERIC(12, 2) NOT NULL,
    line_total         NUMERIC(12, 2) NOT NULL,
    service_type_id    UUID REFERENCES service_types (id),
    inventory_item_id  UUID,
    source             TEXT NOT NULL
);
CREATE INDEX idx_invoice_lines_invoice_id ON invoice_lines (invoice_id);

CREATE TABLE payments (
    id          UUID PRIMARY KEY,
    invoice_id  UUID NOT NULL REFERENCES invoices (id),
    method      TEXT NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    psp_ref     TEXT,
    paid_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payments_invoice_id ON payments (invoice_id);

-- er-diagram.mermaid'de tanimli degil; requirements.md 4.7 "Kasa yonetimi"
-- MVP eklentisi -- basit acilis/kapanis kaydi (mutabakat/fark hesabi yok).
CREATE TABLE cash_register_sessions (
    id                   UUID PRIMARY KEY,
    branch_id            UUID NOT NULL REFERENCES branches (id),
    opened_by_staff_id   UUID NOT NULL REFERENCES staff_users (id),
    opening_balance      NUMERIC(12, 2) NOT NULL,
    opened_at            TIMESTAMPTZ NOT NULL,
    closed_by_staff_id   UUID REFERENCES staff_users (id),
    closing_balance      NUMERIC(12, 2),
    closed_at            TIMESTAMPTZ,
    status               TEXT NOT NULL,
    notes                TEXT
);
CREATE INDEX idx_cash_register_sessions_branch_id ON cash_register_sessions (branch_id);
