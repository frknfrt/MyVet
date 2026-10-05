-- TARBIL P1a (spec 2026-10-04 S13): eklentinin TARBIL stok sayfasindan okudugu anlik goruntu.
-- @TenantId DISINDA (diger tarbil_* tablolariyla ayni karar) -- tenant_id elle filtrelenir.
CREATE TABLE tarbil_stock_snapshot (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    tarbil_system      TEXT NOT NULL,
    taken_at           TIMESTAMPTZ NOT NULL,
    taken_by_staff_id  UUID NOT NULL
);
CREATE INDEX idx_tarbil_stock_snapshot_latest ON tarbil_stock_snapshot (tenant_id, tarbil_system, taken_at DESC);

CREATE TABLE tarbil_stock_snapshot_line (
    id                         UUID PRIMARY KEY,
    snapshot_id                UUID NOT NULL REFERENCES tarbil_stock_snapshot (id) ON DELETE CASCADE,
    tenant_id                  UUID NOT NULL,
    line_no                    INT NOT NULL,
    product_name               TEXT NOT NULL,
    presentation               TEXT,
    lot_number                 TEXT,
    expiry_date                DATE,
    quantity                   INT NOT NULL,
    opened_quantity            NUMERIC(12, 3),
    applied_inventory_item_id  UUID,
    applied_at                 TIMESTAMPTZ
);
CREATE INDEX idx_tarbil_stock_snapshot_line_snapshot ON tarbil_stock_snapshot_line (snapshot_id, line_no);
