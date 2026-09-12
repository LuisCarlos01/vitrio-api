-- Category (spec 004): agrupamento opcional de Product dentro de um Catalog (CONTEXT.md).
CREATE TABLE categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id UUID NOT NULL REFERENCES catalogs (id),
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Toda leitura/escrita de categoria (isolamento por catálogo, ADR-0003) filtra por
-- catalog_id — índice para não degradar conforme o número de categorias cresce.
CREATE INDEX categories_catalog_id_idx ON categories (catalog_id);
