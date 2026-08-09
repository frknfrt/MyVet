ALTER TABLE invoices ADD COLUMN staff_user_id UUID REFERENCES staff_users (id);
CREATE INDEX idx_invoices_staff_user_id ON invoices (staff_user_id);
