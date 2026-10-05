-- TARBIL'de hayvan kimligi Cip No ve Pasaport No ile tutulur; ayri bir 'TARBIL kimlik no' yok (2026-10-04).
-- Alan yanlis adlandirilmisti: girilen degerler pasaport numarasi kabul edilip korunur.
ALTER TABLE patients RENAME COLUMN tarbil_animal_id TO passport_no;
