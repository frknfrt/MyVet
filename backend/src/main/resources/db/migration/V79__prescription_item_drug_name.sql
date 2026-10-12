-- Stoktan verilen recete kaleminin ilaci stok urunudur; katalog ilaci yok, ad anlik goruntusu (spec 2026-10-12).
ALTER TABLE prescription_items ALTER COLUMN drug_id DROP NOT NULL;
ALTER TABLE prescription_items ADD COLUMN drug_name TEXT;
ALTER TABLE prescription_items ADD CONSTRAINT chk_prescription_items_drug
    CHECK (drug_id IS NOT NULL OR drug_name IS NOT NULL);
