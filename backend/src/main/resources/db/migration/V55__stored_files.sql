-- Yeni paylasilan icerik tablosu -- platform/storage/StoredFile.java.
-- id GENERATED DEGIL: her satirin id'si, o dosyayi ilk yaratan satirin
-- (imaging_record_files/lab_result_files) KENDI id'siyle AYNI -- ayri bir
-- arama/join gerekmeden 1:1 eslesir (bkz. tasarim dokumani S4).
CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    content BYTEA NOT NULL,
    content_type TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO stored_files (id, content, content_type, created_at)
SELECT id, content, content_type, uploaded_at FROM imaging_record_files;
INSERT INTO stored_files (id, content, content_type, created_at)
SELECT id, content, content_type, uploaded_at FROM lab_result_files;
