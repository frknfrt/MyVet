CREATE TABLE tenants (
    id                       UUID PRIMARY KEY,
    name                     TEXT NOT NULL,
    tax_number               TEXT,
    kvkk_data_controller_ref TEXT,
    status                   TEXT NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL
);

CREATE TABLE branches (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    name               TEXT NOT NULL,
    address            TEXT,
    city               TEXT,
    tarbil_branch_code TEXT,
    timezone           TEXT
);
CREATE INDEX idx_branches_tenant_id ON branches (tenant_id);

CREATE TABLE subscriptions (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    plan_code      TEXT NOT NULL,
    started_at     DATE NOT NULL,
    renews_at      DATE,
    billing_status TEXT NOT NULL
);
CREATE INDEX idx_subscriptions_tenant_id ON subscriptions (tenant_id);

CREATE TABLE staff_users (
    id                 UUID PRIMARY KEY,
    branch_id          UUID NOT NULL REFERENCES branches (id),
    full_name          TEXT NOT NULL,
    email              TEXT NOT NULL UNIQUE,
    phone              TEXT,
    password_hash      TEXT NOT NULL,
    role               TEXT NOT NULL,
    license_number     TEXT,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_staff_users_branch_id ON staff_users (branch_id);
