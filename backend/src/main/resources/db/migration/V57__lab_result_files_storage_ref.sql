-- storage_ref, mevcut satirin KENDI id'siyle ayni deger -- V55'te stored_files'a
-- tam bu id ile tasindi (bkz. tasarim dokumani S4). Kolon VARCHAR -- entity
-- alani String (FileStoragePort'un opak referans sozlesmesiyle tutarli,
-- plan yazarken UUID'den duzeltildi, bkz. Global Constraints).
ALTER TABLE lab_result_files ADD COLUMN storage_ref VARCHAR(255);
UPDATE lab_result_files SET storage_ref = id::text;
ALTER TABLE lab_result_files ALTER COLUMN storage_ref SET NOT NULL;
ALTER TABLE lab_result_files DROP COLUMN content;
