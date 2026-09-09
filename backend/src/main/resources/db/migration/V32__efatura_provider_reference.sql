-- Asenkron e-Fatura saglayicilari (faturaentegrator.com) icin: POST /invoices
-- yanitinda gelen saglayici tarafi takip ID'si (data.id) burada tutulur --
-- gercek GIB ETTN'i (gib_reference) callback gelene kadar bilinmez.
ALTER TABLE efatura_submission ADD COLUMN provider_reference TEXT;
CREATE INDEX idx_efatura_submission_provider_reference ON efatura_submission (provider_reference);
