ALTER TABLE efatura_submission ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE efatura_submission ADD COLUMN next_retry_at TIMESTAMPTZ;
CREATE INDEX idx_efatura_submission_due_retry ON efatura_submission (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
