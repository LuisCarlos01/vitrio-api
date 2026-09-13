# Spec 007: Logo do catálogo

**Status**: draft

**Contexto**: sétima spec do Vitrio. Bloqueia duas issues do `vitrio-web`: #22
(dashboard — upload de logo por catálogo, ADR 0001/0002 de lá) e #31 (vitrine
pública — header com logo). Continua sobre `Catalog` (specs 001/002) e `Asset`
(spec 003) — reaproveita o upload já existente
(`POST /api/v1/catalogs/{catalogId}/assets`), hoje usado só por `Product`. Termos
seguem [`CONTEXT.md`](../../CONTEXT.md).

**Decisão de transporte**: nenhum endpoint novo de upload — o logo é só mais um
`Asset` do catálogo, vinculado via `PATCH /api/v1/catalogs/{id}` (mesmo endpoint da
spec 002, US1), em vez de um endpoint dedicado (`PUT .../logo`). Consistente com o
resto do domínio (cor, Instagram, nome — tudo passa pelo mesmo `PATCH`) e evita mais
uma rota pra manter em sincronia com CORS/allowedMethods.

## User Stories

### US1 — Definir o logo do catálogo (P1)

Como `Reseller` autenticada, quero enviar uma imagem e vinculá-la como logo do meu
catálogo, para personalizar a vitrine e o dashboard com minha marca.

**Critério de pronto**: `PATCH /api/v1/catalogs/{id}` aceita `logoAssetId`
referenciando um `Asset` já enviado pro mesmo catálogo; `GET`/`PATCH` de catálogo
passam a retornar `logoUrl` resolvido.

**Cenários de aceite**:
1. **Dado** um `Asset` já enviado pro meu catálogo (via
   `POST /api/v1/catalogs/{catalogId}/assets`, spec 003), **quando** envio
   `PATCH /api/v1/catalogs/{id}` com `{ "logoAssetId": "<id do asset>" }`, **então**
   o vínculo é salvo e a resposta traz `logoUrl` apontando pra URL pública do asset.
2. **Dado** um `logoAssetId` que não existe ou pertence a outro catálogo (inclusive
   de outra revendedora), **quando** enviado, **então** a requisição é recusada com
   mensagem clara, sem alterar o logo atual — mesmo isolamento de `Asset` já aplicado
   a `Product` (ADR-0001, spec 004, US2 cenário 2).
3. **Dado** um `PATCH` que **não** inclui `logoAssetId`, **quando** enviado,
   **então** o logo atual permanece inalterado — mesma semântica já estabelecida pra
   todo campo deste endpoint (`null`/ausente = "não alterar", nunca "limpar", spec
   002). Não existe forma de remover um logo já definido nesta spec, só substituí-lo
   por outro (mesmo padrão já aceito pro WhatsApp, spec 002 US2 cenário 4).
4. **Dado** um catálogo que nunca teve logo definido, **quando** consultado (`GET`
   ou `PATCH`), **então** `logoUrl` vem `null` — diferente das cores (`primaryColorHex`/
   `buttonColorHex`), não há logo padrão da aplicação.
5. **Dado** o catálogo de outra revendedora, **quando** tento fazer `PATCH` com
   `logoAssetId` nele, **então** recebo 404 genérico (nunca 403), mesmo padrão de
   isolamento das specs anteriores (ADR-0003).

### US2 — Ver o logo na vitrine pública (P1)

Como `Customer` anônimo, quero ver o logo da revendedora na vitrine pública, para
reconhecer a marca.

**Critério de pronto**: `GET /api/v1/public/catalogs/{slug}` (spec 005) passa a
retornar `logoUrl`.

**Cenários de aceite**:
1. **Dado** um catálogo público com logo definido, **quando** consultado, **então**
   a resposta traz `logoUrl` resolvido, no mesmo formato/local de
   `PublicProductResponse.imageUrl` (URL pública final, nunca o id do asset).
2. **Dado** um catálogo público sem logo definido, **quando** consultado, **então**
   `logoUrl` vem `null` — cliente decide o fallback visual (ex.: iniciais do nome),
   não é responsabilidade do backend.

## Regras de negócio

- `Catalog` ganha o campo `logoAssetId` (opcional, FK para `Asset`, mesmo padrão de
  `Product.imageAssetId` — spec 004). Sem coluna de URL própria: sempre resolvido a
  partir do `Asset` no momento da leitura, igual ao já feito em
  `PublicCatalogService` pra produtos.
- `logoAssetId` sempre validado contra o mesmo `catalogId` do próprio catálogo sendo
  editado — isolamento total já aplicado a `Asset` (ADR-0001, spec 003).
- **Divergência deliberada de `ProductResponse`**: `ProductResponse` (autenticado)
  expõe `imageAssetId` bruto, não uma URL resolvida — quem chama já tem a
  `publicUrl` da resposta de upload (spec 003) e não precisa re-resolver. Para
  `Catalog`, a resposta autenticada (`CatalogResponse`) expõe `logoUrl` já
  resolvido, não `logoAssetId` bruto: o dashboard (`vitrio-web` #22) precisa
  reexibir o logo atual a qualquer momento (ex. depois de um reload de página), sem
  outro endpoint pra resolver asset→URL disponível hoje. Não é inconsistência por
  descuido — é a diferença real de necessidade entre os dois consumidores.
- `logoUrl` exposto em dois lugares: `CatalogResponse` (autenticado — issue #22) e
  `PublicCatalogResponse` (spec 005 — issue #31). Ambos resolvidos do mesmo jeito
  (busca do `Asset` por id, filtrando por `catalogId`), nunca expondo `logoAssetId`
  bruto no lado público (mesmo padrão de `PublicProductResponse.imageUrl`).

## Fora de escopo nesta spec

- Endpoint dedicado pra remover/limpar o logo — só substituição, mesmo padrão já
  aceito pro WhatsApp (spec 002).
- Redimensionamento/recorte/otimização de imagem de logo — mesma decisão de escopo
  da spec 003 (sem normalização, aceita como enviado).
- Resolver o mesmo problema pra `Product.imageAssetId` (dashboard hoje não tem como
  re-resolver a URL de um produto já existente sem o `publicUrl` original) — gap
  real, mas de outra spec já fechada; não é reaberto aqui.
