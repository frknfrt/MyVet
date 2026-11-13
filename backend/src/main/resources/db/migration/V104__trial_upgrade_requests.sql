-- Denemeden ucretli pakete gecis odemesi (spec 2026-11-13 S4.4). iyzico conversationId = id.
-- Fatura odeme basarili olunca kesilir; bu tablo yarim kalan odemeleri de tutar.
CREATE TABLE trial_upgrade_requests (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    plan_code      TEXT NOT NULL,
    billing_period VARCHAR(16) NOT NULL CHECK (billing_period IN ('MONTHLY', 'ANNUAL')),
    coupon_code    TEXT,
    charged_amount NUMERIC(12, 2) NOT NULL,
    status         TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED')),
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_trial_upgrade_requests_tenant ON trial_upgrade_requests (tenant_id);
