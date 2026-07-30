-- Modul 8: online randevu widget'inden gelen talepler henuz bir hekime
-- atanmadan REQUESTED olarak dusuyor -- resepsiyon triyaj sirasinda atar.
ALTER TABLE appointments ALTER COLUMN assigned_staff_id DROP NOT NULL;
