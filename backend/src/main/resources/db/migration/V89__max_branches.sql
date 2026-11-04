-- Plan basina sube sayisi siniri (2026-11-04): daha once hicbir plan sube
-- sayisini kisitlamiyordu, Baslangic planindaki bir klinik de sinirsiz sube
-- ekleyebiliyordu -- Enterprise'in fiyatini haklandiran bir fark yoktu.
-- AI/mesaj kotasiyla ayni desen: NULL = sinirsiz.
ALTER TABLE plans ADD COLUMN max_branches INT;
