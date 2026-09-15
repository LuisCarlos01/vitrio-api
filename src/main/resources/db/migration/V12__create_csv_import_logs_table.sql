-- Spec 009: registro agregado de cada confirmação de importação CSV (spec 006) — só o resumo
-- (aceitos/recusados), sem referência às linhas individuais nem aos produtos criados (fora de
-- escopo, ver specs/009-csv-import-history/spec.md). confirmed_at é o instante em que o
-- PROCESSAMENTO terminou, não o da requisição HTTP (podem divergir por segundos num CSV grande).
CREATE TABLE csv_import_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_id      UUID NOT NULL REFERENCES catalogs (id),
    confirmed_at    TIMESTAMPTZ NOT NULL,
    accepted_count  INTEGER NOT NULL,
    rejected_count  INTEGER NOT NULL
);

-- "Última importação" (GET .../imports/latest) é sempre a mais recente por catalog_id — índice
-- composto pra essa consulta não degradar conforme o histórico cresce.
CREATE INDEX csv_import_logs_catalog_id_confirmed_at_idx ON csv_import_logs (catalog_id, confirmed_at DESC);
