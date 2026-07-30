CREATE TABLE encounter_inventory_usage (
    id                 UUID PRIMARY KEY,
    encounter_id       UUID NOT NULL REFERENCES encounters (id),
    inventory_item_id  UUID NOT NULL,
    quantity           INT NOT NULL
);
CREATE INDEX idx_encounter_inventory_usage_encounter_id ON encounter_inventory_usage (encounter_id);
