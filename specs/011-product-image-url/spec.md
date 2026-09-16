# Spec 011: URL de imagem resolvida em ProductResponse (autenticado)

**Status**: draft

**Contexto**: décima primeira spec do Vitrio. Bloqueia a issue #28 do `vitrio-web`
(layout de `/products`, ADR 0002 de lá): foto do produto na lista e visualizador ao
clicar num card. Continua sobre `Product` (spec 004) e `Asset` (spec 003). Termos
seguem [`CONTEXT.md`](../../CONTEXT.md).

**Problema**: `ProductResponse` (autenticado — `GET`/`PATCH /api/v1/catalogs/{catalogId}/products/{id}`
e a listagem) só expõe `imageAssetId` bruto. Não existe `GET /assets/{id}` nem outro
jeito de resolver esse id pra uma URL no lado autenticado — a única URL disponível é a
`publicUrl` da resposta do `POST /assets`, no momento do upload, perdida depois disso.
Handoff do `vitrio-web` (2026-09-15): isso já era um gap documentado na spec 007
("Fora de escopo", linhas 93-95) como hipotético; virou bloqueio real pra #28.

**Decisão**: substituir `imageAssetId` por `imageUrl` (resolvido) em `ProductResponse`
— mesmo padrão já usado em `CatalogResponse.logoUrl` (spec 007) e
`PublicProductResponse.imageUrl` (spec 005). Isso reverte a "divergência deliberada"
registrada na spec 007 (linhas 74-81): a premissa daquela decisão ("quem chama já tem
a `publicUrl` da resposta de upload e não precisa re-resolver") não se sustenta pro
caso de reexibir um produto já existente, o mesmo motivo que já valia pra `Catalog`.
`imageAssetId` continua sendo o campo de **entrada** em `CreateProductRequest`/
`UpdateProductRequest` — isso não muda, só a resposta de leitura.

## User Stories

### US1 — Ver a foto de um produto já existente (P1)

Como `Reseller` autenticada, quero ver a foto de cada produto no dashboard sem
precisar re-enviar a imagem, para gerenciar meu catálogo sem perder a referência
visual depois de recarregar a página.

**Critério de pronto**: `GET`/`PATCH /api/v1/catalogs/{catalogId}/products/{id}` e
`GET /api/v1/catalogs/{catalogId}/products` (listagem) retornam `imageUrl` (string,
URL pública final do `Asset`) em vez de `imageAssetId` bruto.

**Cenários de aceite**:
1. **Dado** um produto com `imageAssetId` válido, **quando** consulto (detalhe ou
   listagem), **então** a resposta traz `imageUrl` resolvido a partir do `Asset`, no
   mesmo formato/local de `PublicProductResponse.imageUrl` e `CatalogResponse.logoUrl`
   (URL pública final, nunca o id do asset).
2. **Dado** um catálogo com múltiplos produtos, **quando** consulto a listagem,
   **então** a resolução de URL não gera uma consulta ao banco por produto (N+1) —
   mesmo padrão de lote já usado em `PublicCatalogService.getBySlug` pra produtos
   públicos.
3. **Dado** um `PATCH` que envia um novo `imageAssetId` válido (do mesmo catálogo),
   **quando** a resposta retorna, **então** `imageUrl` reflete o novo `Asset`, não o
   anterior.
4. `imageAssetId` continua obrigatório em `CreateProductRequest` e aceito (opcional)
   em `UpdateProductRequest`, com a mesma validação de isolamento já existente
   (`AssetRepository.findByIdAndCatalogId`, ADR-0003) — nenhuma mudança na escrita.

## Regras de negócio

- `imageUrl` é sempre resolvido a partir do `Asset` no momento da leitura, nunca uma
  coluna própria em `Product` — mesma regra já aplicada a `logoUrl` (spec 007).
- Resolução de item único (detalhe, criação, atualização) reusa
  `AssetRepository.resolvePublicUrl(assetId, catalogId)`, já existente. Resolução de
  lista (listagem) busca todos os `Asset` referenciados numa única query
  (`findAllById`), mesmo padrão de `PublicCatalogService.getBySlug`.
- `imageAssetId` nunca é removido do modelo de escrita (`CreateProductRequest`/
  `UpdateProductRequest`) — só da resposta de leitura.

## Fora de escopo nesta spec

- Qualquer endpoint dedicado de resolução de asset (`GET /assets/{id}`) — resolvido
  embutido na resposta de `Product`/`Catalog`, mesma decisão já tomada nas specs
  003/005/007.
- Redimensionamento/otimização de imagem — fora de escopo desde a spec 003.
- Revisitar a divergência equivalente em qualquer outro recurso além de `Product` —
  não há nenhum caso conhecido hoje.
