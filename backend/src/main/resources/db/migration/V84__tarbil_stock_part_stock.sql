-- TARBIL asi stok sayfasindaki "Parca Stok Ara" tablosundan gelen satirlar (2026-10-21). Vetly esitleme ekraninda
-- etiket icin. Eski satirlar ve 0.2.0 eklentiden gelenler butun stok sayilir.
ALTER TABLE tarbil_stock_snapshot_line ADD COLUMN part_stock BOOLEAN NOT NULL DEFAULT FALSE;
