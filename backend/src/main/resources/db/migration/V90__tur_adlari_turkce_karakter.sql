-- V2'de eklenen varsayılan tür adları Türkçe karaktersizdi (Kopek, Sigir, Kanatli).
-- Ekranda doğru görünmeleri için düzeltilir. Yalnızca eski yazımla duran kayıtlar değişir;
-- klinik bu adları Ayarlar > Tür & Irk'tan zaten değiştirdiyse dokunulmaz.
-- TARBİL eklentisi "Köpek" yazımını da köpek olarak tanır (ö -> o normalize eder).
UPDATE species SET name = 'Köpek'   WHERE name = 'Kopek';
UPDATE species SET name = 'Sığır'   WHERE name = 'Sigir';
UPDATE species SET name = 'Kanatlı' WHERE name = 'Kanatli';
