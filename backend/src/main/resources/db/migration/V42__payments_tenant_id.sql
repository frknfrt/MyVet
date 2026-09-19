-- Kat 0: payments.tenant_id, invoices.tenant_id'den (invoice_id uzerinden).
ALTER TABLE payments ADD COLUMN tenant_id UUID;
UPDATE payments t SET tenant_id = i.tenant_id FROM invoices i WHERE t.invoice_id = i.id;
ALTER TABLE payments ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_payments_tenant_id ON payments (tenant_id);
