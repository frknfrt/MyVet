ALTER TABLE plans ADD COLUMN description TEXT;
ALTER TABLE plans ADD COLUMN badge TEXT;
ALTER TABLE plans ADD COLUMN image_url TEXT;
ALTER TABLE plans ADD COLUMN annual_price NUMERIC(10, 2);

CREATE TABLE plan_features (
    plan_id    UUID NOT NULL REFERENCES plans (id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    feature    TEXT NOT NULL,
    PRIMARY KEY (plan_id, sort_order)
);
