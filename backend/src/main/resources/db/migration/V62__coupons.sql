CREATE TABLE coupons (
    id                UUID PRIMARY KEY,
    code              TEXT NOT NULL UNIQUE,
    discount_type     TEXT NOT NULL,
    discount_value    NUMERIC(10, 2) NOT NULL,
    max_redemptions   INTEGER,
    redemption_count  INTEGER NOT NULL DEFAULT 0,
    expires_at        DATE,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL
);

ALTER TABLE tenant_signup_requests ADD COLUMN coupon_code TEXT;
ALTER TABLE tenant_signup_requests ADD COLUMN charged_amount NUMERIC(10, 2);
