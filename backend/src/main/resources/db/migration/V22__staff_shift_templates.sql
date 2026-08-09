CREATE TABLE staff_shift_templates (
    id            UUID PRIMARY KEY,
    staff_user_id UUID NOT NULL REFERENCES staff_users (id),
    day_of_week   TEXT NOT NULL,
    starts_at     TIME NOT NULL,
    ends_at       TIME NOT NULL
);
CREATE INDEX idx_staff_shift_templates_staff_user_id ON staff_shift_templates (staff_user_id);
