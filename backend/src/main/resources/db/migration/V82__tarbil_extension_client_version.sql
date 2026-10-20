-- Faz 7 ek: eklentinin son gordugumuz surumu (X-Client-Version). Min surumu yukseltmeden once
-- kliniklerin guncelleyip guncellemedigini gormek icin. Bos = 0.2.0 oncesi (baslik gondermeyen) eklenti.
ALTER TABLE tarbil_extension_token ADD COLUMN last_client_version TEXT;
