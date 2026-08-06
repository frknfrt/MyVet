ALTER TABLE invoice_lines
    ADD COLUMN discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN vat_rate        NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN vat_amount      NUMERIC(12, 2) NOT NULL DEFAULT 0;
