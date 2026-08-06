CREATE TABLE boarding_rooms (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL REFERENCES tenants (id),
    branch_id    UUID NOT NULL REFERENCES branches (id),
    group_name   TEXT NOT NULL,
    name         TEXT NOT NULL,
    capacity     INT NOT NULL DEFAULT 1,
    daily_rate   NUMERIC(12, 2),
    notes        TEXT,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_boarding_rooms_tenant_id ON boarding_rooms (tenant_id);

CREATE TABLE boarding_stays (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL REFERENCES tenants (id),
    branch_id                 UUID NOT NULL REFERENCES branches (id),
    room_id                   UUID NOT NULL REFERENCES boarding_rooms (id),
    patient_id                UUID NOT NULL REFERENCES patients (id),
    owner_id                  UUID NOT NULL REFERENCES owners (id),
    created_by_staff_id       UUID REFERENCES staff_users (id),
    check_in_date             DATE NOT NULL,
    expected_check_out_date   DATE,
    actual_check_out_date     DATE,
    status                    TEXT NOT NULL,
    notes                     TEXT,
    created_at                TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_boarding_stays_tenant_id ON boarding_stays (tenant_id);
CREATE INDEX idx_boarding_stays_room_id ON boarding_stays (room_id);
CREATE INDEX idx_boarding_stays_patient_id ON boarding_stays (patient_id);

ALTER TABLE invoices ADD COLUMN boarding_stay_id UUID REFERENCES boarding_stays (id);
CREATE INDEX idx_invoices_boarding_stay_id ON invoices (boarding_stay_id);
