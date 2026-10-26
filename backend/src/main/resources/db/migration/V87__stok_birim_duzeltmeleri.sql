-- Stok birimi ve TARBIL miktar duzeltmeleri (spec 2026-10-25). unit kolonuna TABLET de yazilabilir (CHECK yok).
-- piece_size/piece_label: ML kalemde bir pipetin ml'si ve adi (stok ve TARBIL ml; pipet yalniz giris/gosterim).
-- opened_expiry_date: TARBIL "Acilmis Kalan Miktar Son Kullanma Tarihi" (Ilac Takip Sistemi kilavuzu 11.4.7.9).
ALTER TABLE inventory_items ADD COLUMN piece_size NUMERIC(10,2);
ALTER TABLE inventory_items ADD COLUMN piece_label TEXT;
ALTER TABLE inventory_items ADD COLUMN opened_expiry_date DATE;
ALTER TABLE tarbil_stock_snapshot_line ADD COLUMN opened_expiry_date DATE;
