-- Faz 4 (spec 2026-10-15): paket kotalari. NULL = sinirsiz -> mevcut paketlerde davranis degismez.
ALTER TABLE plans ADD COLUMN monthly_ai_quota INTEGER CHECK (monthly_ai_quota >= 0);
ALTER TABLE plans ADD COLUMN monthly_message_quota INTEGER CHECK (monthly_message_quota >= 0);

-- Kiraci + ay (Europe/Istanbul, 'YYYY-MM') + tur basina sayac. Platform duzeyi tablo;
-- erisim yalnizca UsageQuotaPort uzerinden (MonthlyUsageStore, native SQL).
CREATE TABLE tenant_monthly_usage (
    tenant_id UUID        NOT NULL,
    period    CHAR(7)     NOT NULL,
    kind      VARCHAR(16) NOT NULL,
    used      INTEGER     NOT NULL DEFAULT 0 CHECK (used >= 0),
    PRIMARY KEY (tenant_id, period, kind)
);
