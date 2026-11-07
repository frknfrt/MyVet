-- Veri aktarimi (baska veteriner yazilimindan sahip/hasta alma) -- Faz 1.
-- Geriye uyumlu: yalnizca yeni tablolar.

CREATE TABLE import_batches (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID        NOT NULL REFERENCES tenants (id),
    source_system               TEXT        NOT NULL,
    file_name                   TEXT        NOT NULL,
    status                      TEXT        NOT NULL,
    source_file                 BYTEA,
    report                      JSONB       NOT NULL,
    total_rows                  INT         NOT NULL DEFAULT 0,
    processed_rows              INT         NOT NULL DEFAULT 0,
    rejected_match_source_ids   JSONB       NOT NULL DEFAULT '[]',
    error_message               TEXT,
    created_by_admin_id         UUID        NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    committed_at                TIMESTAMPTZ,
    rolled_back_at              TIMESTAMPTZ
);

CREATE INDEX idx_import_batches_tenant_created ON import_batches (tenant_id, created_at DESC);

CREATE TABLE import_record_links (
    id                  UUID PRIMARY KEY,
    batch_id            UUID        NOT NULL REFERENCES import_batches (id),
    tenant_id           UUID        NOT NULL,
    source_system       TEXT        NOT NULL,
    entity_type         TEXT        NOT NULL,
    source_id           TEXT        NOT NULL,
    target_id           UUID        NOT NULL,
    created_by_import   BOOLEAN     NOT NULL,
    legacy_data         JSONB       NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_import_record_links_source UNIQUE (tenant_id, source_system, entity_type, source_id)
);

CREATE INDEX idx_import_record_links_target ON import_record_links (tenant_id, entity_type, target_id);
CREATE INDEX idx_import_record_links_batch ON import_record_links (batch_id);
