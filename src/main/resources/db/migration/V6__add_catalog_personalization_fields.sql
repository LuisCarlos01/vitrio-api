-- Spec 002: personalização de identidade e contato do catálogo. Cores ficam nullable aqui de
-- propósito — o valor padrão é aplicado na camada de aplicação (CatalogResponse), não no banco,
-- pra manter "revendedora nunca customizou" distinguível de "customizou igual ao default".
ALTER TABLE catalogs
    ADD COLUMN primary_color_hex           TEXT,
    ADD COLUMN button_color_hex            TEXT,
    ADD COLUMN instagram_handle            TEXT,
    ADD COLUMN whatsapp_number             TEXT,
    ADD COLUMN whatsapp_verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN whatsapp_verified_at        TIMESTAMPTZ;
