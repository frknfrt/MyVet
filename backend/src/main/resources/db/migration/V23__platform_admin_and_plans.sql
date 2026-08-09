CREATE TABLE platform_admin_users (
    id            UUID PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name     TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE plans (
    id            UUID PRIMARY KEY,
    code          TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    monthly_price NUMERIC(10, 2) NOT NULL,
    active        BOOLEAN NOT NULL DEFAULT TRUE
);
