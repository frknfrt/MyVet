-- Plan basina personel (staff_user) sayisi siniri (2026-11-05): max_branches ile ayni desen
-- (spec 2026-11-04 sube limiti genisletildi). Vitrin sayfasi "1 Kullanici / Hekim Hesabi" (Baslangic),
-- "3 Kullaniciya Kadar Personel Yonetimi" (Profesyonel) diye vaat ediyordu ama hicbir yerde
-- zorlanmiyordu -- InviteStaffMemberUseCase'de hicbir sayim/sinir kontrolu yoktu.
-- NULL = sinirsiz (mevcut planlarda davranis degismez, elle doldurulmali).
ALTER TABLE plans ADD COLUMN max_staff_users INT;
