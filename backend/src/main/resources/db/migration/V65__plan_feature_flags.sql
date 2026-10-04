CREATE TABLE plan_feature_flags (
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    feature TEXT NOT NULL,
    PRIMARY KEY (plan_id, feature)
);
