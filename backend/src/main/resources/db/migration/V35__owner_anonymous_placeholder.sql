-- Kayitsiz/yoldan gecen musteriler icin tenant basina tek bir sentinel
-- owner kaydi (Hizli Satis ozelligi, bkz. docs/superpowers/specs/2026-09-14-hizli-satis-design.md).
ALTER TABLE owners ADD COLUMN is_anonymous_placeholder BOOLEAN NOT NULL DEFAULT FALSE;
