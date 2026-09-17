# Spec 012: Exclusão de Asset (rollback de upload órfão)

**Status**: draft

**Contexto**: décima segunda spec do Vitrio. Origem: issue #21, handoff do `vitrio-web`
(`CreateProductForm` sobe a imagem primeiro via `POST /catalogs/{catalogId}/assets` e só
depois cria o produto; se a criação falhar — ex. SKU duplicado —, o `Asset` já existe e
fica órfão no S3/banco). Bloqueia a issue `vitrio-web#16`. Continua sobre `Asset`
(spec 003). Termos seguem [`CONTEXT.md`](../../CONTEXT.md).

**Decisão de escopo**: só o endpoint de exclusão. Trocar o `CreateProductForm` do
`vitrio-web` pra de fato chamar esse endpoint no rollback é o desdobramento da
`vitrio-web#16`, feito depois que este endpoint existir e for mergeado.

## User Stories

### US1 — Excluir um Asset não utilizado (P1)

Como `Reseller` autenticada (via cliente `vitrio-web`), quero excluir um `Asset` que
enviei mas acabei não usando (ex.: criação do produto falhou depois do upload), para
não acumular imagens órfãs no meu catálogo.

**Critério de pronto**: `DELETE /api/v1/catalogs/{catalogId}/assets/{id}` remove o
registro em `AssetRepository` e o objeto em `AssetStorage` (S3).

**Cenários de aceite**:
1. **Dado** um `Asset` do meu catálogo, não referenciado por nenhum `Product` ou
   `Catalog`, **quando** excluo, **então** o registro some do banco e o objeto some do
   S3 — uma tentativa de leitura subsequente do `publicUrl` retornaria 404 do S3 (fora
   do controle desta API, mas é o efeito esperado).
2. **Dado** um `Asset` já referenciado por `Product.imageAssetId` **ou**
   `Catalog.logoAssetId` (de qualquer catálogo — a referência é o que importa, não o
   dono), **quando** tento excluir, **então** a requisição é recusada com `409
   Conflict`, sem remover nada (nem banco, nem S3). Não deve ser possível derrubar a
   imagem de um produto/loja já existente por engano.
3. **Dado** um `Asset` que não existe, **quando** tento excluir, **então** `404
   Not Found` genérico.
4. **Dado** um `Asset` que existe mas pertence a outro catálogo (do mesmo dono ou de
   outro), **quando** tento excluir pelo `catalogId` errado, **então** o mesmo `404`
   do cenário 3 — nunca revela que o asset existe em outro catálogo.
5. **Dado** um catálogo que não existe ou não é meu, **quando** tento excluir
   qualquer asset nele, **então** `404` genérico (mesmo padrão de isolamento de
   `AssetService.upload`, ADR-0003) — checado antes de qualquer busca de asset.

## Regras de negócio

- Isolamento: mesmo padrão de `AssetService.upload` — `catalogRepository.findByIdAndOwnerId`
  primeiro, 404 genérico se o catálogo não existir ou não for do dono (ADR-0003).
- Busca do asset: `assetRepository.findByIdAndCatalogId(id, catalogId)`, mesma função já
  usada por `Product`/`Catalog` pra validar `imageAssetId`/`logoAssetId` — "não existe" e
  "existe mas é de outro catálogo" viram o mesmo 404.
- Checagem de uso: um `Asset` é considerado em uso se existir qualquer `Product` com esse
  `imageAssetId` (`ProductRepository.existsByImageAssetId`) **ou** qualquer `Catalog` com
  esse `logoAssetId` (`CatalogRepository.existsByLogoAssetId`) — checado antes de
  qualquer exclusão física, para que a operação seja tudo-ou-nada.
- Ordem de exclusão física: remove primeiro do `AssetStorage` (S3), depois do
  `AssetRepository` — se a exclusão no S3 falhar, a transação não chega a remover o
  registro do banco, evitando um `Asset` "fantasma" (banco sem registro, mas objeto
  ainda no S3, que é o cenário inofensivo) virar o inverso (registro no banco sem
  objeto no S3, que quebraria `publicUrl` de qualquer coisa que ainda referenciasse
  esse id — embora a checagem de uso já devesse impedir isso).

## Fora de escopo nesta spec

- Atualizar `CreateProductForm` (`vitrio-web`) pra chamar este endpoint no rollback —
  desdobramento da `vitrio-web#16`, feito numa sessão separada depois do merge.
- Exclusão em lote ou qualquer rotina de limpeza automática de assets órfãos —
  este endpoint é só a operação unitária explícita, chamada pelo próprio cliente que
  causou a órfã.
- Restaurar/desfazer uma exclusão — permanente, mesmo padrão de exclusão de produto
  (issue #10) e catálogo.
