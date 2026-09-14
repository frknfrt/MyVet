-- national_id_masked hic kullanilmiyordu (Faz 1'de gercek TCKN bilerek
-- tutulmuyordu, bkz. docs/implementation-plan.md "TCKN karari"). Bu karar
-- kullaniciyla birlikte degistirildi: artik gercek TCKN bu kolonda tutuluyor,
-- bu yuzden yeni bir kolon acmak yerine mevcut (bos) kolon yeniden adlandirildi.
ALTER TABLE owners RENAME COLUMN national_id_masked TO national_id;

-- Nullable: mevcut kayitlarda doğum tarihi yok, zorunluluk uygulama/API
-- katmaninda saglanir (patients.birth_date ile ayni yaklasim).
ALTER TABLE owners ADD COLUMN birth_date DATE;
