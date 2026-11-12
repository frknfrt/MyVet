ALTER TABLE tenant_signup_requests
    ADD COLUMN billing_period VARCHAR(16) NOT NULL DEFAULT 'MONTHLY'
    CHECK (billing_period IN ('MONTHLY', 'ANNUAL'));

ALTER TABLE subscriptions
    ADD COLUMN billing_period VARCHAR(16) NOT NULL DEFAULT 'MONTHLY'
    CHECK (billing_period IN ('MONTHLY', 'ANNUAL'));
