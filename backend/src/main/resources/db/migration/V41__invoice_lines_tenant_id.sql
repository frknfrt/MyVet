-- Kat 0: invoice_lines.tenant_id, invoices.tenant_id'den (invoice_id uzerinden).
ALTER TABLE invoice_lines ADD COLUMN tenant_id UUID;
UPDATE invoice_lines t SET tenant_id = i.tenant_id FROM invoices i WHERE t.invoice_id = i.id;
ALTER TABLE invoice_lines ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_invoice_lines_tenant_id ON invoice_lines (tenant_id);
