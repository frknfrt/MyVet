-- TARBIL P2 (spec 2026-10-04 P2 S3.1): asi kaydinin stok kalemi. FK yok (moduller arasi kimlik); yalniz sutun eklenir.
ALTER TABLE vaccination_records ADD COLUMN inventory_item_id UUID;
