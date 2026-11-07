ALTER TABLE payments ADD COLUMN request_id UUID;
CREATE UNIQUE INDEX uq_payments_invoice_request ON payments (tenant_id, invoice_id, request_id)
    WHERE request_id IS NOT NULL;

ALTER TABLE boarding_stays ADD CONSTRAINT ck_boarding_departure_dates CHECK (
    (expected_check_out_date IS NULL OR expected_check_out_date >= check_in_date) AND
    (actual_check_out_date IS NULL OR actual_check_out_date >= check_in_date)
) NOT VALID;

CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appointments ADD CONSTRAINT appointments_staff_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, assigned_staff_id WITH =, tstzrange(scheduled_start, scheduled_end, '[)') WITH &&)
    WHERE (assigned_staff_id IS NOT NULL AND status IN ('CONFIRMED','CHECKED_IN','IN_PROGRESS'));
