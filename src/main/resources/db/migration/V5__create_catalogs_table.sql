-- Catalog é a fronteira de tenancy do Vitrio (ADR-0001, docs/adr/0001-catalog-as-tenancy-boundary.md),
-- não a Reseller: uma conta pode ter N catálogos, cada um isolado dos outros. owner_id é uma FK
-- direta para users, sem tabela de membership — o MVP não tem conceito de colaborador por catálogo.
CREATE TABLE catalogs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   UUID NOT NULL REFERENCES users (id),
    name       TEXT NOT NULL,
    slug       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Slug é único globalmente (define a URL pública), não só por dono — spec 001.
CREATE UNIQUE INDEX catalogs_slug_unique_idx ON catalogs (slug);

-- Toda listagem/isolamento por dono (ADR-0003) filtra por owner_id — índice para não
-- degradar conforme o número de catálogos cresce.
CREATE INDEX catalogs_owner_id_idx ON catalogs (owner_id);
