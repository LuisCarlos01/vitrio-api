# Spec 004: Produtos e categorias do catálogo

**Status**: draft

**Contexto**: quarta spec do Vitrio, cobre o CRUD manual de `Product`/`Category` da
jornada "Personalizar catálogo" do `ideia.md`. Dependia da spec 003 (upload de
`Asset`) existir, já que `Product.imageAssetId` é obrigatório — todo produto exige uma
imagem já enviada antes de ser criado. Termos seguem [`CONTEXT.md`](../../CONTEXT.md),
estados de produto (`isVisible`/`isOrderable`/`isActive`) já documentados lá.

## User Stories

### US1 — Gerenciar categorias do catálogo (P1)

Como `Reseller` autenticada, quero criar, listar, renomear e excluir categorias do meu
catálogo, para organizar meus produtos.

**Critério de pronto**: CRUD completo de `Category` sob
`/api/v1/catalogs/{catalogId}/categories`, com o mesmo isolamento por catálogo das
specs anteriores.

**Cenários de aceite**:
1. **Dado** um catálogo meu, **quando** crio uma categoria com nome válido, **então**
   ela é persistida vinculada a esse catálogo.
2. **Dado** um catálogo meu com categorias, **quando** listo, **então** recebo só as
   categorias desse catálogo (nunca de outro, nem de outra revendedora).
3. **Dado** uma categoria minha, **quando** excluo, **então** ela é removida e todo
   `Product` que a referenciava passa a não ter categoria (`categoryId = null`) — a
   exclusão nunca apaga produtos.
4. **Dado** um catálogo que não é meu, **quando** tento criar/listar/renomear/excluir
   uma categoria nele, **então** recebo 404 genérico (nunca 403), mesmo padrão de
   isolamento das specs anteriores (ADR-0003).
5. **Dado** o `id` de uma categoria de outro catálogo (meu ou de outra revendedora),
   **quando** tento renomear/excluir usando o `catalogId` de um catálogo diferente do
   dela, **então** recebo 404 genérico — uma categoria só existe no contexto do seu
   próprio catálogo.

### US2 — Cadastrar produto (P1)

Como `Reseller` autenticada, quero cadastrar um produto no meu catálogo com nome,
imagem e quantidade disponível, para exibi-lo depois na vitrine pública.

**Critério de pronto**: `POST /api/v1/catalogs/{catalogId}/products` cria o produto já
com uma imagem obrigatória (`imageAssetId`, referenciando um `Asset` já enviado pra
esse mesmo catálogo).

**Cenários de aceite**:
1. **Dado** um catálogo meu com menos de 50 produtos, **quando** cadastro um produto
   com nome e `imageAssetId` válidos (asset já pertence a esse catálogo), **então** é
   criado com `isVisible=false`, `isOrderable=false`, `isActive=true`,
   `quantityAvailable=0` por padrão — a revendedora ativa manualmente depois de
   revisar (mesma cautela do fluxo de importação do `ideia.md`, aplicada aqui também
   ao cadastro manual).
2. **Dado** um `imageAssetId` que não existe ou pertence a outro catálogo (inclusive
   outro catálogo da mesma revendedora), **quando** tento cadastrar, **então** a
   requisição é recusada — um produto nunca referencia um asset de fora do seu
   catálogo (mesmo isolamento do ADR-0001 aplicado a `Asset`).
3. **Dado** um catálogo meu já com 50 produtos, **quando** tento cadastrar mais um,
   **então** a requisição é recusada com mensagem clara (limite do MVP, `ideia.md`).
4. **Dado** um `sku` preenchido, **quando** já existe outro produto com o mesmo `sku`
   nesse catálogo, **então** a requisição é recusada — SKU é único por catálogo
   quando preenchido (repetível entre catálogos diferentes).
5. **Dado** um `categoryId` preenchido, **quando** não existe ou pertence a outro
   catálogo, **então** a requisição é recusada — mesma regra de pertencimento do
   `imageAssetId`.
6. **Dado** um `categoryId` omitido, **quando** cadastro, **então** o produto é criado
   sem categoria (opcional, spec 004 CONTEXT.md).

### US3 — Editar produto (P1)

Como `Reseller` autenticada, quero editar nome, descrição, SKU, categoria, imagem,
quantidade e os três estados do meu produto, para manter o catálogo atualizado.

**Critério de pronto**: `PATCH /api/v1/catalogs/{catalogId}/products/{id}` atualiza só
os campos enviados (mesmo padrão null-safe da spec 002).

**Cenários de aceite**:
1. **Dado** um produto meu, **quando** envio só `{ "quantityAvailable": 10 }`,
   **então** só esse campo muda — os demais permanecem como estavam.
2. **Dado** um produto meu visível, **quando** desativo (`isActive=false`), **então**
   ele deixa de aparecer na vitrine pública e de ser pedível, mas o cadastro é
   preservado (CONTEXT.md, "Estados do produto").
3. **Dado** um produto meu desativado, **quando** reativo (`isActive=true`), **então**
   `isVisible`/`isOrderable` **não** voltam automaticamente ao que eram antes de
   desativar — precisam ser reativados explicitamente, se for o caso.
4. **Dado** um novo `sku` no `PATCH` que colide com outro produto do mesmo catálogo,
   **então** a requisição é recusada, mesma regra da US2 cenário 4.
5. **Dado** um novo `imageAssetId`/`categoryId` no `PATCH` que não pertence ao mesmo
   catálogo, **então** a requisição é recusada, mesma regra da US2 cenários 2 e 5.
6. **Dado** um produto de outro catálogo (meu ou de outra revendedora), **quando**
   tento editá-lo usando um `catalogId` que não é o dele, **então** recebo 404
   genérico.

### US4 — Listar e consultar produtos (P1)

Como `Reseller` autenticada, quero listar e consultar os produtos do meu catálogo,
para gerenciá-los.

**Critério de pronto**: `GET /api/v1/catalogs/{catalogId}/products` e
`GET /api/v1/catalogs/{catalogId}/products/{id}` retornam todos os produtos do
catálogo (visíveis ou não, ativos ou não) — esta spec é a visão de gerenciamento da
`Reseller`, não a vitrine pública do `Customer` (fora de escopo, ver abaixo).

**Cenários de aceite**:
1. **Dado** um catálogo meu com produtos em qualquer estado, **quando** listo,
   **então** recebo todos, independente de `isVisible`/`isOrderable`/`isActive`.
2. **Dado** um catálogo que não é meu, **quando** tento listar/consultar produtos
   dele, **então** recebo 404 genérico.

### US5 — Excluir produto (P2)

Como `Reseller` autenticada, quero excluir permanentemente um produto do meu
catálogo, quando quero removê-lo de vez (diferente de desativar).

**Critério de pronto**: `DELETE /api/v1/catalogs/{catalogId}/products/{id}` remove o
registro definitivamente. A confirmação explícita ("não pode ser desfeita") é
responsabilidade do frontend (`ideia.md`) — o backend só executa a exclusão quando
chamado.

**Cenários de aceite**:
1. **Dado** um produto meu, **quando** excluo, **então** ele deixa de existir (não é
   o mesmo que `isActive=false`).
2. **Dado** um produto de outro catálogo, **quando** tento excluí-lo, **então** recebo
   404 genérico.

## Regras de negócio

- `Category`: `id`, `catalogId` (FK), `name` (obrigatório, texto livre, sem
  unicidade exigida nesta spec).
- `Product`: `id`, `catalogId` (FK), `name` (obrigatório), `sku` (opcional, único por
  catálogo quando preenchido), `description` (opcional), `imageAssetId` (obrigatório,
  FK para `Asset` do mesmo catálogo), `categoryId` (opcional, FK para `Category` do
  mesmo catálogo), `quantityAvailable` (inteiro ≥ 0, default 0),
  `isVisible`/`isOrderable` (default `false` nos dois — mesma cautela do fluxo de
  importação do `ideia.md`, aplicada aqui ao cadastro manual: a revendedora revisa e
  ativa manualmente), `isActive` (default `true` — produto nasce ativo, só fica
  inativo se a revendedora desativar explicitamente).
- Limite de 50 produtos por catálogo (`ideia.md`) — checado na criação, nunca na
  edição de um produto já existente.
- SKU único por catálogo quando preenchido (repetível entre catálogos diferentes,
  inclusive da mesma revendedora) — sem detecção de duplicidade textual além da
  igualdade exata.
- `imageAssetId` e `categoryId` sempre validados contra o mesmo `catalogId` do
  produto — um produto nunca referencia asset/categoria de outro catálogo, mesmo
  isolamento total já aplicado a `Asset` (ADR-0001, spec 003).
- Excluir uma `Category` desvincula (`categoryId = null`) os produtos que a
  referenciavam, nunca os exclui.
- Desativar (`isActive=false`) preserva o cadastro; excluir (`DELETE`) remove
  definitivamente — duas ações distintas (CONTEXT.md, "Estados do produto").
- Reativar (`isActive=true`) não restaura `isVisible`/`isOrderable` automaticamente.
- Isolamento: todo acesso a `Category`/`Product` por id é validado contra o
  `catalogId` da URL, e o `catalogId` contra o dono autenticado — 404 genérico
  (nunca 403) em qualquer um dos dois níveis, mesmo padrão das specs 001–003
  (ADR-0003).

## Fora de escopo nesta spec

- Importação via CSV — jornada própria do `ideia.md` ("Importar catálogo"), com suas
  próprias regras de preview/revisão; spec seguinte.
- Catálogo público por slug (endpoint consumido pelo `Customer` anônimo, sem
  autenticação) e carrinho — ainda não especificado; esta spec cobre só a visão de
  gerenciamento da `Reseller` autenticada.
- Busca/filtro/paginação na listagem de produtos — lista completa é suficiente
  enquanto o limite for 50 produtos por catálogo.
- Reordenar produtos dentro de uma categoria ou entre categorias.
- Qualquer edição da imagem em si (crop, múltiplas imagens por produto) — um produto
  referencia exatamente um `Asset` (spec 003, sem normalização/HEIC nesta versão).
- Exclusão de categoria em lote ou renomear/mesclar categorias automaticamente.
