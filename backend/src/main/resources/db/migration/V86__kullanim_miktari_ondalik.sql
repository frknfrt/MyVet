-- Kullanim miktari ondalik (spec 2026-10-24): ml ile kullanilan ilacta 1,5 ml gibi miktarlar. settled_source: TARBIL'de
-- dusumun yapildigi liste (WHOLE | OPEN); eski satirlarda null.
ALTER TABLE pending_stock_deduction ALTER COLUMN quantity TYPE NUMERIC(12,2);
ALTER TABLE pending_stock_deduction ALTER COLUMN settled_quantity TYPE NUMERIC(12,2);
ALTER TABLE pending_stock_deduction ADD COLUMN settled_source TEXT;
ALTER TABLE prescription_items ALTER COLUMN stock_quantity TYPE NUMERIC(12,2);
ALTER TABLE encounter_inventory_usage ALTER COLUMN quantity TYPE NUMERIC(12,2);
