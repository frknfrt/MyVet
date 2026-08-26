CREATE TABLE staff_invites (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL REFERENCES tenants (id),
    branch_id                 UUID NOT NULL REFERENCES branches (id),
    email                     TEXT NOT NULL,
    full_name                 TEXT NOT NULL,
    role                      TEXT NOT NULL,
    token                     TEXT NOT NULL UNIQUE,
    status                    TEXT NOT NULL,
    invited_by_staff_user_id  UUID NOT NULL REFERENCES staff_users (id),
    created_at                TIMESTAMPTZ NOT NULL,
    expires_at                TIMESTAMPTZ NOT NULL,
    accepted_at               TIMESTAMPTZ
);
CREATE INDEX idx_staff_invites_tenant_id ON staff_invites (tenant_id);
