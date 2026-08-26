ALTER TABLE encounters ADD COLUMN physical_exam_findings jsonb NOT NULL DEFAULT '[]'::jsonb;
