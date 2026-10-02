-- Hekimin TARBIL'de ilk kez elle sectigi degerlerden ogrenilen esletirmeler (klinik bazli).
-- @TenantId DISINDA (tarbil_sync_log ile ayni karar) -- tenant_id elle filtrelenir.
CREATE TABLE tarbil_value_mapping (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    kind                TEXT NOT NULL,
    vetly_key           TEXT NOT NULL,
    tarbil_fields       JSONB NOT NULL,
    learned_by_staff_id UUID NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_tarbil_value_mapping UNIQUE (tenant_id, kind, vetly_key)
);
