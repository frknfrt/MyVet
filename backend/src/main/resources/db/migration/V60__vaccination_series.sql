-- Periyodik asi serisi: bir protokolu (orn. "60 gunde 10 doz") tek seferde
-- N adet vaccination_records kaydi olarak olusturabilmek icin.
-- series_id ayni seriye ait kayitlari gruplar; dose_number/dose_total sadece
-- seriye ait kayitlarda doludur, tekil (seri disi) kayitlarda NULL kalir.
ALTER TABLE vaccination_records ADD COLUMN series_id UUID;
ALTER TABLE vaccination_records ADD COLUMN dose_number INTEGER;
ALTER TABLE vaccination_records ADD COLUMN dose_total INTEGER;
CREATE INDEX idx_vaccination_records_series_id ON vaccination_records (series_id);
