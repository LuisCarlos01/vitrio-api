-- Spec 007: logo do catálogo. Nullable de propósito, mesmo padrão de primary_color_hex (V6) —
-- "nunca definiu logo" é distinguível de qualquer outro estado, sem logo padrão da aplicação
-- (diferente das cores). Sem ON DELETE: um Asset referenciado como logo não pode ser excluído
-- (nenhum endpoint de exclusão de Asset existe hoje, spec 003).
ALTER TABLE catalogs
    ADD COLUMN logo_asset_id UUID REFERENCES assets (id);
