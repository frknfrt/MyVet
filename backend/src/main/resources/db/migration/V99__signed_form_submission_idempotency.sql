CREATE TABLE signed_form_submissions (
    tenant_id UUID NOT NULL,
    request_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    signed_form_id UUID REFERENCES signed_forms(id) DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (tenant_id, request_id)
);
CREATE INDEX idx_signed_form_submissions_form ON signed_form_submissions(signed_form_id);
