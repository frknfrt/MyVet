-- TARBIL stok dusum senkronu (spec 2026-10-09): TARBIL'de dusum onaylanana kadar Vetly stogu dusmez.
CREATE TABLE pending_stock_deduction (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    inventory_item_id   UUID NOT NULL REFERENCES inventory_items (id),
    quantity            INT  NOT NULL CHECK (quantity > 0),
    reference_type      TEXT NOT NULL,
    reference_id        UUID NOT NULL,
    status              TEXT NOT NULL,
    settled_via         TEXT,
    settled_quantity    INT,
    settled_at          TIMESTAMPTZ,
    settled_by_staff_id UUID,
    created_at          TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_pending_stock_deduction_ref ON pending_stock_deduction (reference_type, reference_id, inventory_item_id);
CREATE INDEX idx_pending_stock_deduction_tenant_status ON pending_stock_deduction (tenant_id, status);

-- Muayenesiz recete, TARBIL teshisi ve "klinigin stogundan ver" kalemleri (spec S5.6).
ALTER TABLE prescriptions ALTER COLUMN encounter_id DROP NOT NULL;
ALTER TABLE prescriptions ADD COLUMN tarbil_disease_id UUID REFERENCES tarbil_disease (id);
ALTER TABLE prescription_items ADD COLUMN inventory_item_id UUID REFERENCES inventory_items (id);
ALTER TABLE prescription_items ADD COLUMN stock_quantity INT CHECK (stock_quantity IS NULL OR stock_quantity > 0);

-- Aktarim kaynagi (PRESCRIPTION belgesi muayeneden ya da receteden), teshis ve dusum notu.
ALTER TABLE tarbil_submission ADD COLUMN source_kind TEXT;
UPDATE tarbil_submission SET source_kind = document_type;
ALTER TABLE tarbil_submission ADD COLUMN tarbil_disease_id UUID REFERENCES tarbil_disease (id);
ALTER TABLE tarbil_submission ADD COLUMN settlement_note TEXT;
