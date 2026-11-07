ALTER TABLE appointments ADD CONSTRAINT ck_appointment_interval CHECK (scheduled_end > scheduled_start) NOT VALID;
CREATE INDEX ix_invoices_page ON invoices (tenant_id, issued_at DESC NULLS LAST, id);
