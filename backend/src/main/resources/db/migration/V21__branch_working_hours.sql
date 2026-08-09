CREATE TABLE branch_working_hours (
    id          UUID PRIMARY KEY,
    branch_id   UUID NOT NULL REFERENCES branches (id),
    day_of_week TEXT NOT NULL,
    closed      BOOLEAN NOT NULL DEFAULT FALSE,
    opens_at    TIME,
    closes_at   TIME,
    UNIQUE (branch_id, day_of_week)
);
CREATE INDEX idx_branch_working_hours_branch_id ON branch_working_hours (branch_id);
