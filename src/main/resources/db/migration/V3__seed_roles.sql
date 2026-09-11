-- Papéis padrão do RBAC enxuto (ver ADR-0001 do sentinel-auth-api). Nomes sem prefixo
-- "ROLE_" — a conversão para GrantedAuthority é responsabilidade da camada de auth, não
-- deste schema. ADMIN nunca é atribuível via cadastro público (só existe se inserido
-- manualmente aqui/por seed futuro) — todo registro público recebe RESELLER
-- (ver ADR-0001 do Vitrio, docs/adr/0001-catalog-as-tenancy-boundary.md).
INSERT INTO roles (name) VALUES ('ADMIN'), ('RESELLER');
