-- TARBIL P1a (spec 2026-10-04 S13): stok kaleminin TARBIL'deki karsiligi ve birimi.
ALTER TABLE inventory_items ADD COLUMN tarbil_system TEXT;
ALTER TABLE inventory_items ADD COLUMN tarbil_product_name TEXT;
ALTER TABLE inventory_items ADD COLUMN tarbil_presentation TEXT;
ALTER TABLE inventory_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'ADET';
