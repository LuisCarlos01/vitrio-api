-- Product (spec 004): item cadastrado dentro de um Catalog (CONTEXT.md). imageAssetId é
-- obrigatório (ideia.md) — todo produto exige uma imagem já enviada (spec 003) antes de existir.
CREATE TABLE products (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id         UUID NOT NULL REFERENCES catalogs (id),
    name               TEXT NOT NULL,
    sku                TEXT,
    description        TEXT,
    image_asset_id     UUID NOT NULL REFERENCES assets (id),
    -- SET NULL, não CASCADE nem ausência de ON DELETE: excluir uma Category desvincula os
    -- produtos que a referenciavam, nunca os exclui (spec 004, US1 cenário 3 / issue #7).
    category_id        UUID REFERENCES categories (id) ON DELETE SET NULL,
    quantity_available INTEGER NOT NULL DEFAULT 0,
    is_visible         BOOLEAN NOT NULL DEFAULT false,
    is_orderable       BOOLEAN NOT NULL DEFAULT false,
    is_active          BOOLEAN NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Toda leitura/escrita de produto (isolamento por catálogo, ADR-0003) filtra por catalog_id —
-- índice para não degradar conforme o número de produtos cresce.
CREATE INDEX products_catalog_id_idx ON products (catalog_id);

-- SKU único por catálogo quando preenchido (spec 004) — índice parcial, já que múltiplos
-- produtos sem SKU (NULL) no mesmo catálogo são permitidos. Rede de segurança pra checagem
-- já feita em ProductService contra corrida de escrita concorrente.
CREATE UNIQUE INDEX products_catalog_id_sku_unique_idx ON products (catalog_id, sku) WHERE sku IS NOT NULL;
