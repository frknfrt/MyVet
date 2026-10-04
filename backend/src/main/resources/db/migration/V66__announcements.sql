CREATE TABLE announcements (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    created_by_admin_id UUID NOT NULL,
    created_by_admin_email TEXT NOT NULL,
    recipient_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_announcements_created_at ON announcements (created_at DESC);
