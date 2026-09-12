-- Asset (spec 003): referência normalizada de uma imagem no storage, sempre vinculada a um único
-- catalog (CONTEXT.md) — nunca compartilhada entre catálogos, mesmo isolamento total do ADR-0001.
CREATE TABLE assets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id   UUID NOT NULL REFERENCES catalogs (id),
    storage_key  TEXT NOT NULL,
    content_type TEXT NOT NULL,
    byte_size    BIGINT NOT NULL,
    public_url   TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Toda leitura/escrita de asset (isolamento por catálogo, ADR-0003) filtra por catalog_id —
-- índice para não degradar conforme o número de assets cresce.
CREATE INDEX assets_catalog_id_idx ON assets (catalog_id);
