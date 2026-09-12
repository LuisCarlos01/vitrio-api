# Spec 003: Upload de imagens (Asset) via S3

**Status**: draft

**Contexto**: terceira spec do Vitrio. Fundação de upload de imagem — sem ela,
`Product` (imagem obrigatória) e `HeroBanner` (carrossel) ficam bloqueados. Continua
sobre o `Catalog` das specs 001/002. Termos seguem [`CONTEXT.md`](../../CONTEXT.md).

**Decisão de transporte**: diferente do `wacatolog` (que fazia upload direto do
navegador pro storage por causa do limite de 4,5MB de payload da Vercel, serverless),
o `vitrio-api` roda em EC2 própria, sem esse limite de plataforma. Upload
**multipart direto pra API** (`POST`, `multipart/form-data`) é suficiente e mais
simples aqui — decisão específica deste contexto de infraestrutura, não uma
divergência por descuido do padrão de referência.

**Decisão de escopo (MVP)**: WebP, HEIC/HEIF e normalização automática de formato
ficam fora desta versão — o `ImageIO` padrão do Java só lê nativamente JPEG/PNG (sem
plugin extra); WebP e HEIC exigiriam dependência nativa no SO ou licença comercial,
custo de infra desproporcional pro MVP. Formatos aceitos nesta spec: JPEG, PNG,
guardados no S3 no formato em que chegaram, sem conversão. Ver "Fora de escopo nesta
spec".

## User Stories

### US1 — Enviar imagem pro catálogo (P1)

Como `Reseller` autenticada, quero enviar uma imagem pro meu catálogo, para usá-la
depois num produto ou banner.

**Critério de pronto**: `POST /api/v1/catalogs/{catalogId}/assets` recebe um arquivo,
valida formato/tamanho, normaliza, envia pro S3 e persiste um registro `Asset`
vinculado ao catálogo.

**Cenários de aceite**:
1. **Dado** uma imagem JPEG/PNG válida até 10MB, **quando** enviada, **então** o
   sistema envia pro bucket S3 configurado, e persiste um `Asset` com `catalogId`,
   referência de storage e URL pública.
2. **Dado** um arquivo cujo conteúdo real não corresponde a nenhum dos formatos
   aceitos (verificado pelos bytes reais, não só pela extensão ou `Content-Type`
   declarado), **quando** enviado, **então** é recusado com mensagem clara, mesmo
   que a extensão pareça válida.
3. **Dado** um arquivo maior que 10MB, **quando** enviado, **então** é recusado antes
   de qualquer normalização/upload pro S3.
4. **Dado** um catálogo que não é meu, **quando** tento enviar uma imagem pra ele,
   **então** recebo 404 genérico (nunca 403), mesmo padrão de isolamento das specs
   anteriores (ADR-0003).
5. **Dado** um upload bem-sucedido, **quando** consulto a URL pública retornada,
   **então** a imagem é servida diretamente do S3, sem passar pela API do
   `vitrio-api` (bucket com leitura pública configurada, mesmo padrão do
   `wacatolog`: caminho gerado pelo sistema, não-adivinhável, como proteção — não
   controle de acesso por sessão).

## Regras de negócio

- `Asset`: `id`, `catalogId` (FK, dono da imagem), `storageKey` (caminho no S3,
  gerado pelo sistema — nunca o nome de arquivo original do usuário), `contentType`,
  `byteSize`, `publicUrl`, `createdAt`.
- Formatos aceitos: JPEG, PNG — verificados pelo conteúdo real do arquivo
  (magic bytes), não pela extensão nem pelo `Content-Type` do request.
- Tamanho máximo: 10MB por arquivo.
- Sem normalização nesta versão: a imagem é gravada no S3 no mesmo formato em que
  chegou (JPEG/PNG), sem conversão. Ver "Fora de escopo nesta spec".
- Bucket S3: leitura pública (qualquer um com a URL acessa a imagem), escrita restrita
  à aplicação (credenciais do backend). Path gerado pelo sistema
  (`{catalogId}/{assetId}.{extensão real do formato}`, ex. `.jpg`/`.png`),
  nunca o nome de arquivo enviado pelo usuário — evita colisão e vazamento de nome de
  arquivo original.
- Isolamento: `catalogId` do path sempre validado contra o dono autenticado, mesmo
  padrão 404 genérico das specs 001/002 (ADR-0003).
- Um asset pertence a exatamente um catálogo; não é compartilhável entre catálogos
  (nem da mesma revendedora) — consistente com o isolamento total entre catálogos já
  decidido (ADR-0001).

## Fora de escopo nesta spec

- Vincular o asset a um `Product` ou `HeroBanner` — specs seguintes (004 em diante),
  que vão referenciar `assetId` já existente.
- Exclusão de asset / limpeza de órfãos (asset enviado mas nunca vinculado a nada) —
  aceitável por enquanto, mesmo risco aceito documentado no `wacatolog` (ADR-0008
  dele) pra um caso parecido.
- Suporte a WebP, HEIC/HEIF e normalização automática pra um formato único de
  exibição — adiado pra uma v2. `ImageIO` padrão não lê WebP nem HEIC nativamente;
  toda biblioteca Java viável pra decodificar HEIC e/ou codificar WebP hoje exige
  dependência nativa no SO da EC2 (ex. `libheif`/`libwebp`) ou licença comercial (ex.
  JDeli); custo desproporcional pro MVP, já que fotos de iPhone continuam
  funcionando manualmente (usuária pode reexportar como JPEG antes de enviar).
  Quando essa v2 acontecer, é aditiva: entra como uma etapa a mais no pipeline
  (validar → **converter** → subir) e um formato a mais na validação, sem mudar o
  contrato do endpoint.
- Qualquer edição de imagem (crop, redimensionamento manual pela revendedora).
- Múltiplos assets num único request (upload em lote) — um arquivo por vez.
