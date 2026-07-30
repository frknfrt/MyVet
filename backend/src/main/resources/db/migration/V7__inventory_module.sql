CREATE TABLE inventory_items (
    id                 UUID PRIMARY KEY,
    branch_id          UUID NOT NULL REFERENCES branches (id),
    sku_barcode        TEXT,
    name               TEXT NOT NULL,
    category           TEXT,
    quantity_on_hand   INT NOT NULL,
    reorder_threshold  INT NOT NULL,
    expiry_date        DATE,
    lot_number         TEXT,
    unit_cost          NUMERIC(12, 2)
);
CREATE INDEX idx_inventory_items_branch_id ON inventory_items (branch_id);

CREATE TABLE stock_movements (
    id                 UUID PRIMARY KEY,
    inventory_item_id  UUID NOT NULL REFERENCES inventory_items (id),
    movement_type      TEXT NOT NULL,
    quantity           INT NOT NULL,
    reference_type     TEXT NOT NULL,
    reference_id       UUID,
    created_at         TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_stock_movements_inventory_item_id ON stock_movements (inventory_item_id);

-- Modul 5'te inventory_items henuz yokken olusturulan invoice_lines.inventory_item_id
-- icin FK'yi simdi ekliyoruz.
ALTER TABLE invoice_lines
    ADD CONSTRAINT fk_invoice_lines_inventory_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id);

-- encounter_inventory_usage (V6) icin de ayni sekilde.
ALTER TABLE encounter_inventory_usage
    ADD CONSTRAINT fk_encounter_inventory_usage_item FOREIGN KEY (inventory_item_id) REFERENCES inventory_items (id);
