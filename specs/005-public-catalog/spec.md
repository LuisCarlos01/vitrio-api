# Spec 005: Catálogo público por slug

**Status**: draft

**Contexto**: quinta spec do Vitrio, cobre a jornada "Compartilhar no WhatsApp" do
`ideia.md` na sua metade de leitura: o `Customer` anônimo acessa o link público do
catálogo (`vitrio.com/{slug}`) e vê os produtos disponíveis, sem autenticar. Continua
sobre `Catalog`/`Category`/`Product` das specs 001, 002 e 004. Termos seguem
[`CONTEXT.md`](../../CONTEXT.md).

**Decisão de escopo**: fecha também a pendência deixada em aberto na issue #9 (spec
004) — o critério "desativar oculta o produto" só podia ser provado quando este
endpoint existisse.

## User Stories

### US1 — Ver catálogo público por slug (P1)

Como `Customer` anônimo, quero acessar o catálogo de uma revendedora pelo link
público, para ver os produtos disponíveis sem precisar de conta.

**Critério de pronto**: `GET /api/v1/public/catalogs/{slug}` retorna a identidade do
catálogo e a lista de produtos efetivamente públicos, sem exigir autenticação.

**Cenários de aceite**:
1. **Dado** um slug de catálogo existente, **quando** consultado, **então** retorna
   nome, cores de identidade, Instagram, categorias do catálogo e a lista de produtos
   — sem exigir Access token.
2. **Dado** um produto com `isActive=true` e `isVisible=true`, **quando** o catálogo é
   consultado, **então** esse produto aparece na lista, com `sku`, `description`,
   `imageUrl` (resolvido a partir do `Asset`), `categoryId`, `quantityAvailable` e
   `isOrderable`.
3. **Dado** um produto com `isActive=false` **ou** `isVisible=false`, **quando** o
   catálogo é consultado, **então** esse produto **não aparece** na resposta — filtro
   no servidor, nunca apenas ocultação no cliente (CONTEXT.md, "Estados do produto").
4. **Dado** um produto visível mas com `isOrderable=false` ou `quantityAvailable=0`,
   **quando** o catálogo é consultado, **então** o produto aparece na lista (pode ser
   consultado), mas com os campos que permitem ao cliente saber que não pode ser
   pedido (`ideia.md`: "produto visível e indisponível pode ser consultado, mas não
   entra no carrinho").
5. **Dado** um catálogo com `whatsappVerificationStatus` diferente de `VERIFIED`,
   **quando** consultado, **então** `whatsappNumber` vem `null` na resposta — número
   não verificado não é anunciado publicamente.
6. **Dado** um catálogo com WhatsApp verificado, **quando** consultado, **então**
   `whatsappNumber` vem preenchido.
7. **Dado** um slug que não corresponde a nenhum catálogo, **quando** consultado,
   **então** retorna 404 genérico.

## Regras de negócio

- Endpoint público: sem `Authentication`, sem verificação de dono — o `slug` é o único
  identificador aceito (nunca o `id` interno do catálogo, mesma decisão da spec 001:
  slug é endereçamento, não credencial).
- Filtro de produtos: só entram na resposta os com `isActive=true` **e**
  `isVisible=true`. Esse filtro acontece na query do backend, nunca é responsabilidade
  do cliente.
- `isOrderable`/`quantityAvailable` continuam nos produtos retornados (não filtram a
  presença do produto) — permitem ao frontend decidir se mostra o botão de adicionar
  ao carrinho, sem esconder o produto em si.
- `imageUrl`: resolvido a partir do `imageAssetId` do produto (URL pública do S3, spec
  003) — a resposta pública nunca expõe o `imageAssetId` bruto, só a URL final.
- Categorias: todas as categorias do catálogo são retornadas, independente de terem
  produto público vinculado no momento (não é dado sensível, evita lógica extra de
  filtro).
- `whatsappNumber`: só aparece na resposta se `whatsappVerificationStatus == VERIFIED`;
  caso contrário, vem `null` (frontend decide ocultar o botão de WhatsApp).
- Sem busca, filtro ou paginação no backend — lista completa, mesma decisão da spec
  004 (limite de 50 produtos por catálogo torna isso viável); busca/filtro no
  `Customer` é responsabilidade do frontend sobre a lista já carregada.
- CORS: rota pública, mesma configuração de origem permitida já usada pelas rotas
  autenticadas (`vitrio.cors.allowed-origins`), sem exceção adicional.

## Fora de escopo nesta spec

- Carrinho e finalização via WhatsApp (`wa.me` com mensagem pré-formatada) — 100%
  client-side, sem endpoint de backend, conforme `ideia.md`.
- Hero banners (carrossel) — `HeroBanner` ainda não existe como entidade neste repo;
  spec própria futura.
- Busca/filtro/paginação server-side — decisão explícita acima.
- Analytics de visita ou qualquer rastreio de acesso ao catálogo público.
- Modo claro/escuro — comportamento 100% de frontend (mesma decisão da spec 002).
