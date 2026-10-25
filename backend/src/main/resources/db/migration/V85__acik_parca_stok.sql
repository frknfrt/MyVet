-- Acik stok (ilac) ve parca stok (asi), spec 2026-10-24. Kalem iki parcali: kapali adet (quantity_on_hand) + acik miktar.
-- Mevcut 'unit' kolonu (V73) kullanim birimi olur: ADET | ML | G | FLAKON. Mevcut kalemler ADET, acik 0 (spec 3.8).
ALTER TABLE inventory_items ADD COLUMN package_size NUMERIC(10,2);
ALTER TABLE inventory_items ADD COLUMN opened_quantity NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE inventory_items ADD COLUMN unit_source TEXT NOT NULL DEFAULT 'AUTO';
ALTER TABLE stock_movements ADD COLUMN opened_delta NUMERIC(12,2);
ALTER TABLE tarbil_stock_snapshot_line ADD COLUMN doses_per_package INTEGER;
ALTER TABLE tarbil_stock_snapshot ADD COLUMN part_stock_read BOOLEAN NOT NULL DEFAULT FALSE;
