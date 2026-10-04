-- TARBIL P2 son inceleme: ayni asi kaydi icin en fazla bir OUT ve bir IN stok hareketi (es zamanli istekte cift dusmeye karsi).
CREATE UNIQUE INDEX uq_stock_movements_vaccination ON stock_movements (reference_id, movement_type) WHERE reference_type = 'VACCINATION';
