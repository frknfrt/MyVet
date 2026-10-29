-- Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29):
-- bu is kayitlari bir muayeneye (encounter) bagli degil, cunku lab/imaging
-- sonuclari encounter'dan bagimsiz. ai_jobs.encounter_id NOT NULL kisitini kaldir.
ALTER TABLE ai_jobs ALTER COLUMN encounter_id DROP NOT NULL;
