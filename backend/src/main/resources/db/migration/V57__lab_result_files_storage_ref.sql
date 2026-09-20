-- storage_ref, mevcut satirin KENDI id'siyle ayni deger -- V55'te stored_files'a
-- tam bu id ile tasindi (bkz. tasarim dokumani S4). Kolon VARCHAR -- entity
-- alani String (FileStoragePort'un opak referans sozlesmesiyle tutarli,
-- plan yazarken UUID'den duzeltildi, bkz. Global Constraints).

-- V55'in kopyasi sadece o an var olan satirlari kapsar -- iki instance'in
-- ayni anda calistigi bir deploy'da (rolling/blue-green), V55 commit
-- olduktan SONRA ama bu migration calismadan ONCE eklenen bir satir,
-- stored_files'ta karsiligi olmadan storage_ref alip DROP COLUMN ile
-- baytlarini kaybedebilirdi. Bu INSERT, V55'in kacirmis olabilecegi
-- satirlari da kapsayarak pencereyi YAPISAL OLARAK kapatir.
INSERT INTO stored_files (id, content, content_type, created_at)
SELECT f.id, f.content, f.content_type, f.uploaded_at
FROM lab_result_files f
WHERE NOT EXISTS (SELECT 1 FROM stored_files s WHERE s.id = f.id);

ALTER TABLE lab_result_files ADD COLUMN storage_ref VARCHAR(255);
UPDATE lab_result_files SET storage_ref = id::text;
ALTER TABLE lab_result_files ALTER COLUMN storage_ref SET NOT NULL;

-- Guvenlik kontrolu: backfill eksikse DROP COLUMN'dan once migration'i
-- durdur -- sessizce veri kaybetmek yerine deploy'u basarisiz kilariz.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM lab_result_files f
        WHERE NOT EXISTS (SELECT 1 FROM stored_files s WHERE s.id::text = f.storage_ref)
    ) THEN
        RAISE EXCEPTION 'storage_ref backfill incomplete -- aborting before DROP COLUMN content';
    END IF;
END $$;

ALTER TABLE lab_result_files DROP COLUMN content;
