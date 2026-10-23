CREATE TABLE admin_sent_emails (
    id UUID PRIMARY KEY,
    template_type TEXT NOT NULL,
    recipient_email TEXT NOT NULL,
    subject TEXT NOT NULL,
    sent_by_admin_id UUID NOT NULL,
    sent_by_admin_email TEXT NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_admin_sent_emails_sent_at ON admin_sent_emails (sent_at DESC);
