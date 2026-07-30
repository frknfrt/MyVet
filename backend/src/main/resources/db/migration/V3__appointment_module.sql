CREATE TABLE service_types (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenants (id),
    name                  TEXT NOT NULL,
    default_duration_min  INT NOT NULL,
    default_price         NUMERIC(10, 2) NOT NULL
);
CREATE INDEX idx_service_types_tenant_id ON service_types (tenant_id);

CREATE TABLE appointments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    branch_id           UUID NOT NULL REFERENCES branches (id),
    patient_id          UUID NOT NULL REFERENCES patients (id),
    owner_id            UUID NOT NULL REFERENCES owners (id),
    assigned_staff_id   UUID NOT NULL REFERENCES staff_users (id),
    service_type_id     UUID NOT NULL REFERENCES service_types (id),
    scheduled_start     TIMESTAMPTZ NOT NULL,
    scheduled_end       TIMESTAMPTZ NOT NULL,
    status              TEXT NOT NULL,
    no_show_risk_score  NUMERIC(3, 2),
    source              TEXT NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_appointments_branch_id ON appointments (branch_id);
CREATE INDEX idx_appointments_owner_id ON appointments (owner_id);
CREATE INDEX idx_appointments_staff_schedule ON appointments (assigned_staff_id, scheduled_start, scheduled_end);
