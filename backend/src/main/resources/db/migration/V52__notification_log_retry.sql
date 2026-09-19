ALTER TABLE notification_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE notification_log ADD COLUMN next_retry_at TIMESTAMPTZ;
CREATE INDEX idx_notification_log_due_retry ON notification_log (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
