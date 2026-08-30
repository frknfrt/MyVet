CREATE TABLE tenant_signup_requests (
    id               UUID PRIMARY KEY,
    clinic_name      TEXT NOT NULL,
    admin_full_name  TEXT NOT NULL,
    admin_email      TEXT NOT NULL,
    phone            TEXT,
    plan_code        TEXT NOT NULL,
    status           TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

ALTER TABLE staff_invites ALTER COLUMN invited_by_staff_user_id DROP NOT NULL;
