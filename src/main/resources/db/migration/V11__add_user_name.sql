-- Spec 008: perfil da revendedora. Nullable de propósito — "nunca definiu nome" (comum logo
-- após o cadastro, já que name não entra em POST /api/v1/auth/register) precisa ser
-- distinguível de qualquer valor definido; sem default de aplicação, diferente das cores do
-- catálogo (não existe "nome padrão" sensato pra inventar a partir do email aqui no backend).
ALTER TABLE users
    ADD COLUMN name VARCHAR(255);
