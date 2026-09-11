# Spec 001: Cadastro de conta e criação de catálogo

**Status**: draft

**Contexto**: primeira spec do Vitrio. Fundação de acesso — uma `Reseller` cria a
própria conta (cadastro público, decisão registrada em
[ADR-0002](../../docs/adr/0002-public-registration-open.md)) e, a partir daí, cria um
ou mais `Catalog`s próprios (fronteira de tenancy, ver
[ADR-0001](../../docs/adr/0001-catalog-as-tenancy-boundary.md)). Termos usados aqui
seguem o glossário em [`CONTEXT.md`](../../CONTEXT.md).

Base de código: reaproveita o módulo de autenticação do `sentinel-auth-api`
(JWT access 15min + refresh opaco 7 dias rotativo, Argon2id), adaptando o `Role`
existente (`ADMIN`/`USER`) para `ADMIN`/`RESELLER` e removendo `USER`.

## User Stories

### US1 — Cadastro público de conta (P1)

Como visitante com o link do Vitrio, quero criar uma conta com email e senha para
poder montar meus catálogos.

**Critério de pronto**: com um email não usado antes, o cadastro cria a conta com
papel `RESELLER` e permite login imediato.

**Cenários de aceite**:
1. **Dado** um email ainda não cadastrado e uma senha válida, **quando** a pessoa se
   registra, **então** a conta é criada com papel `RESELLER` e ela recebe token de
   acesso (login automático pós-registro, mesmo comportamento do
   `sentinel-auth-api`).
2. **Dado** um email já cadastrado, **quando** a pessoa tenta se registrar de novo,
   **então** o cadastro é recusado com mensagem clara, sem expor se a diferença é o
   email ou outra coisa.
3. **Dado** uma senha com menos de 8 caracteres, **quando** a pessoa tenta se
   registrar, **então** o cadastro é recusado por validação (mesma regra do
   `sentinel-auth-api`: `@Size(min = 8)`).
4. Não existe nenhum caminho, público ou não, que resulte em papel `ADMIN` — esse
   papel só existe via seed manual no banco.
5. **Dado** um IP que excedeu o limite de tentativas de registro numa janela de
   tempo, **quando** tenta se registrar de novo, **então** a requisição é recusada
   por rate limit, sem criar conta.

### US2 — Criar catálogo (P1)

Como `Reseller` autenticada, quero criar um catálogo informando um nome, para
começar a montar minha vitrine.

**Critério de pronto**: com uma conta autenticada, criar um catálogo gera um
registro com `ownerId` apontando pra essa conta e um slug público derivado do nome.

**Cenários de aceite**:
1. **Dado** uma `Reseller` autenticada e um nome de catálogo (ex.: "Catálogo -
   Boticário"), **quando** ela cria o catálogo, **então** o sistema gera um slug em
   kebab-case a partir do nome (ex.: `catalogo-boticario`) e persiste o catálogo com
   `ownerId` = conta autenticada.
2. **Dado** um slug gerado que já existe (de qualquer revendedora), **quando** o
   catálogo é criado, **então** o sistema acrescenta um sufixo numérico incremental
   (`catalogo-boticario-2`, `-3`, ...) até achar um slug livre — nunca falha por
   colisão de slug.
3. **Dado** uma `Reseller` autenticada, **quando** ela cria um segundo, terceiro ou
   N-ésimo catálogo, **então** a criação é aceita sem limite de quantidade de
   catálogos por revendedora (só o limite de 50 produtos é por catálogo, não aqui).
4. O slug nunca é editável diretamente pela revendedora nesta spec — é
   sempre derivado do nome no momento da criação (edição de slug fica fora de
   escopo; se ela quiser outro link, cria outro catálogo ou muda o nome, o que
   *não* deve recalcular o slug existente — ver regras abaixo).

### US3 — Listar meus catálogos (P2)

Como `Reseller` autenticada, quero ver a lista dos catálogos que criei, para escolher
qual editar.

**Critério de pronto**: a listagem retorna só os catálogos cujo `ownerId` é a conta
autenticada. Inclui também `GET /catalogs/{id}` (detalhe de um catálogo específico),
com a mesma restrição de posse.

**Cenários de aceite**:
1. **Dado** uma `Reseller` com dois catálogos, **quando** ela lista seus catálogos,
   **então** vê os dois, nunca catálogos de outra revendedora.
2. **Dado** uma `Reseller` recém-criada sem catálogos, **quando** ela lista,
   **então** recebe uma lista vazia (não erro).

### US4 — Isolamento entre revendedoras (P1, caso negativo)

Como sistema, preciso impedir que uma `Reseller` veja o detalhe de um catálogo de
outra, mesmo sabendo o `id`.

**Cenários de aceite**:
1. **Dado** dois catálogos de duas revendedoras distintas, **quando** a revendedora
   A tenta buscar o detalhe (`GET /catalogs/{id}`) do catálogo da revendedora B,
   **então** a operação é negada sem revelar se o catálogo existe (404 genérico, não
   403) — consistente com
   [ADR-0003](../../docs/adr/0003-tenant-isolation-in-application-layer.md). Edição e
   exclusão de catálogo ficam fora de escopo nesta spec (ver abaixo).

## Regras de negócio

- Papel default de todo cadastro público: `RESELLER`. `ADMIN` nunca é atribuível via
  API.
- Email: normalizado para minúsculas antes de comparar/persistir (herdado do
  `sentinel-auth-api` — índice único case-insensitive), evitando cadastro duplicado
  ou falha de login por capitalização.
- Rate limiting: `/register` passa a ser limitado por IP (Bucket4j + Caffeine em
  memória, mesma mecânica já usada em `/login` no `sentinel-auth-api`, ADR-0010
  dele), com limite mais permissivo que login — o suficiente pra impedir criação de
  contas em massa, já que o endpoint é público (ADR-0002) e antes não era limitado.
  `/login` mantém o rate limiting por IP e por email já existente no
  `sentinel-auth-api` (tentativas de senha) — só reaproveitado, sem mudança.
- Slug: kebab-case (minúsculas, hífen), derivado do nome do catálogo no momento da
  criação; único globalmente; colisão resolvida com sufixo numérico automático;
  imutável após criação nesta spec (renomear o catálogo não recalcula o slug — evita
  quebrar link já compartilhado no WhatsApp).
- `Catalog.ownerId`: FK direta pra `User`, sem tabela de membership (ADR-0001).
- Isolamento: toda leitura/escrita de `Catalog` filtra por `ownerId` = usuário
  autenticado, na camada de serviço (ADR-0003).

## Fora de escopo nesta spec

- Personalização do catálogo (cores, banner, WhatsApp, Instagram) — spec seguinte.
- Edição/exclusão de catálogo — spec seguinte (junto com o diálogo de confirmação de
  exclusão permanente).
- Produtos, categorias, importação CSV — specs seguintes.
- Recuperação de senha, verificação de email — não fazem parte do MVP (herdado do
  `sentinel-auth-api`, que também não implementa). Sem esse fluxo, reset de senha só
  acontece manualmente no banco, feito pelo admin — aceitável enquanto a única
  usuária é a avó do mantenedor. Revisitar se o produto ganhar mais usuárias.
