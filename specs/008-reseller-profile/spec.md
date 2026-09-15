# Spec 008: Perfil da revendedora (nome e "quem sou eu")

**Status**: draft

**Contexto**: oitava spec do Vitrio. Bloqueia a issue #26 do `vitrio-web` (overview
do dashboard, ADR 0002 de lá — seção "Catálogo"): a saudação "Olá, {nome}" não tem
nenhum dado pra puxar hoje, e o `vitrio-web` não tem nenhum jeito de descobrir dados
da própria conta logada além do JWT bruto. Continua sobre `User`/`Reseller`
(spec 001). Termos seguem [`CONTEXT.md`](../../CONTEXT.md).

**Decisão de escopo**: `name` **não** entra no cadastro (`POST /api/v1/auth/register`
continua só email+senha, spec 001 inalterada) — só fica editável depois, via o
endpoint novo desta spec. Evita mexer no formulário de registro já existente no
`vitrio-web` e no fluxo de auth (rate limit, validação, mensagens de erro já
fechados na spec 001) por uma necessidade que é só de exibição no dashboard. Custo:
recém-cadastrada nunca tem nome no primeiro acesso — aceitável, o `vitrio-web` decide
o fallback de exibição (ex.: parte local do email) enquanto o campo estiver vazio,
não é responsabilidade do backend inventar um nome a partir do email.

## User Stories

### US1 — Consultar meus dados (P1)

Como `Reseller` autenticada, quero consultar meus próprios dados de conta, para o
dashboard saber quem eu sou sem depender só do conteúdo do JWT.

**Critério de pronto**: `GET /api/v1/users/me` retorna id, email e nome (`null` se
nunca definido) da conta do Access token usado.

**Cenários de aceite**:
1. **Dado** um Access token válido, **quando** consulto `GET /api/v1/users/me`,
   **então** recebo meu `id`, `email` e `name` (`null` se nunca definido) —
   nunca dados de outra conta.
2. **Dado** nenhum Access token (ou um expirado/inválido), **quando** consulto esse
   endpoint, **então** recebo 401 — mesmo padrão de qualquer rota autenticada.
3. Este endpoint não aceita `id` como parâmetro nem de nenhuma outra forma — "eu" é
   sempre resolvido do token, nunca de um valor enviado pelo cliente (evita um
   caminho pra IDOR trivial).

### US2 — Definir/editar meu nome (P1)

Como `Reseller` autenticada, quero definir ou trocar meu nome de exibição, para
personalizar a saudação do dashboard.

**Critério de pronto**: `PATCH /api/v1/users/me` aceita `name` e persiste.

**Cenários de aceite**:
1. **Dado** um `name` de até 255 caracteres, **quando** enviado via `PATCH`,
   **então** é salvo e passa a aparecer em `GET /api/v1/users/me`.
2. **Dado** um `PATCH` sem `name` no corpo, **quando** enviado, **então** o nome
   atual permanece inalterado — mesma semântica "ausente = não altera" já usada em
   `PATCH /api/v1/catalogs/{id}` (spec 002).
3. **Dado** um `name` vazio (string vazia, não ausente), **quando** enviado,
   **então** é recusado por validação — evita um "nome" visualmente vazio na
   saudação; para limpar de fato não há endpoint (mesmo padrão já aceito pro
   WhatsApp do catálogo e pro logo, specs 002/007: só substituir, nunca limpar).
4. **Dado** um `name` maior que 255 caracteres, **quando** enviado, **então** é
   recusado por validação, sem alterar o nome atual.

## Regras de negócio

- `User` ganha o campo `name` (opcional, `VARCHAR(255)`, nullable — sem valor padrão
  da aplicação, diferente das cores do catálogo; `null` significa "nunca definiu",
  e fica assim até a revendedora explicitamente definir um).
- `GET`/`PATCH /api/v1/users/me` resolvem a conta exclusivamente a partir do
  `Authentication` do request (mesmo padrão de `ownerId` já usado em
  `CatalogController`/`ProductController` — nunca de um parâmetro de path/query).
- Nenhuma relação com `GET /api/v1/users` (admin-only, spec 001/US do RBAC) — são
  dois endpoints com propósitos e autorizações completamente diferentes; este ganha
  rotas próprias (`/me`), não reaproveita `UserController`/`UserService` existentes
  além de compartilhar a entidade `User`.

## Fora de escopo nesta spec

- `name` no cadastro (`POST /api/v1/auth/register`) — decisão de escopo acima.
- Qualquer outro campo de perfil (foto, telefone, bio) — só o necessário pra
  desbloquear a issue #26 do `vitrio-web`.
- Exclusão de conta / alteração de email/senha — specs futuras, se necessário.
