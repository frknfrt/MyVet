-- Musteri (owner) ve hasta (patient) detay sayfalari icin Shepherd/Kolayvet
-- referanslarindan derlenen ek alanlar.

ALTER TABLE owners
    ADD COLUMN middle_name           TEXT,
    ADD COLUMN secondary_phone       TEXT,
    ADD COLUMN city                  TEXT,
    ADD COLUMN district              TEXT,
    ADD COLUMN occupation            TEXT,
    ADD COLUMN referral_source       TEXT,
    ADD COLUMN client_discount       NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN critical_alert        TEXT,
    ADD COLUMN notes                 TEXT,
    ADD COLUMN sms_consent           BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN whatsapp_consent      BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN notification_consent  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN protocol_number       TEXT;

ALTER TABLE patients
    ADD COLUMN color                 TEXT,
    ADD COLUMN temperament           TEXT,
    ADD COLUMN distinguishing_marks  TEXT,
    ADD COLUMN is_aggressive         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blood_type            TEXT,
    ADD COLUMN food_brand            TEXT,
    ADD COLUMN critical_alert        TEXT,
    ADD COLUMN notes                 TEXT,
    ADD COLUMN protocol_number       TEXT,
    ADD COLUMN rabies_tag            TEXT;
