# Spec 013: Redimensionamento e compressão de imagem no upload

**Status**: draft

**Contexto**: décima terceira spec do Vitrio. Origem: issue #22, achada investigando
lentidão na vitrine pública em produção (2026-09-17). `AssetService` só valida tamanho
em bytes (10MB, spec 003) — nenhuma validação/ajuste de dimensão, e `S3AssetStorage`
salva os bytes recebidos sem nenhum processamento. Uma foto de produto tirada direto
do celular (8-9MB, resolução nativa) fica salva assim no S3 pra sempre, servida por
inteiro pra cada visitante antes de qualquer otimização de entrega do lado do
`vitrio-web` (`next/image`, já mitigado lá). Continua sobre `Asset` (spec 003).
Termos seguem [`CONTEXT.md`](../../CONTEXT.md).

**Decisões de triagem** (issue #22 marcava isso como não-óbvio, decidido nesta sessão):
- Biblioteca: [Thumbnailator](https://github.com/coobird/thumbnailator) — puro Java
  sobre `ImageIO` (já usado por `ImageFormat`), sem dependência nativa (roda numa
  `t3.micro` de 1GB RAM sem instalar ImageMagick/libvips na instância).
- Resolução alvo: lado maior limitado a 1600px, configurável
  (`vitrio.asset.max-dimension-px`), não hardcoded.
- Qualidade JPEG: 0.82, configurável (`vitrio.asset.jpeg-quality`).
- PNG: só redimensionado, sem compressão de qualidade — `ImageIO`/Thumbnailator não
  tem knob de qualidade pra PNG (lossless); comprimir de verdade exigiria quantização
  (ex. pngquant), fora de escopo. Ver "Fora de escopo".

## User Stories

### US1 — Upload salva uma versão otimizada da imagem (P1)

Como `Reseller`, quero que a foto que envio pro meu catálogo/produto seja salva numa
versão já otimizada pra web, para que minha vitrine carregue rápido pros meus
clientes sem eu precisar comprimir a imagem manualmente antes de enviar.

**Critério de pronto**: `POST /api/v1/catalogs/{catalogId}/assets` continua aceitando
até 10MB de entrada (validação inalterada), mas o objeto salvo no S3 é redimensionado
e (quando JPEG) recomprimido antes do `PutObject`.

**Cenários de aceite**:
1. **Dado** uma imagem JPEG com o lado maior acima de 1600px, **quando** enviada,
   **então** o `Asset` salvo tem o lado maior redimensionado para 1600px (proporção
   preservada) e foi recomprimido com qualidade 0.82 — `byteSize` do `Asset` salvo é
   menor que o tamanho do arquivo original enviado.
2. **Dado** uma imagem JPEG com o lado maior já menor ou igual a 1600px, **quando**
   enviada, **então** não é redimensionada (evita upscaling artificial) — só passa
   pela recompressão de qualidade.
3. **Dado** uma imagem PNG com o lado maior acima de 1600px, **quando** enviada,
   **então** é redimensionada para 1600px no lado maior, permanece PNG (transparência
   preservada), sem alteração de qualidade além do redimensionamento.
4. **Dado** um arquivo de até 10MB que passa na validação de formato existente
   (`ImageFormat.detect`), **quando** processado, **então** o processamento nunca
   falha por causa do redimensionamento em si — qualquer JPEG/PNG válido nesse
   tamanho é suportado pelo Thumbnailator.
5. **Dado** o fluxo de logo do catálogo (`Catalog.logoAssetId`, spec 007) e o de foto
   de produto (`Product.imageAssetId`, spec 004), **quando** qualquer um faz upload,
   **então** ambos passam pelo mesmo processamento — `AssetService` é o único ponto
   de entrada de upload pros dois, nenhuma duplicação de lógica entre eles.

## Regras de negócio

- Processamento acontece em `AssetService`, entre a validação de formato
  (`ImageFormat.detect`) e o `assetStorage.upload` — os bytes que chegam no
  `AssetStorage`/S3 já são os otimizados, nunca os originais.
- Limite de entrada continua 10MB (`MAX_FILE_SIZE_BYTES`), checado antes do
  processamento — rejeita cedo, sem gastar CPU redimensionando um arquivo que já
  seria rejeitado de qualquer forma.
- `byteSize`/`contentType` persistidos em `Asset` refletem o arquivo **depois** do
  processamento (o que foi de fato salvo no S3), nunca o original recebido.
- Dimensão e qualidade configuráveis via `application.yml`
  (`vitrio.asset.max-dimension-px`, default 1600; `vitrio.asset.jpeg-quality`,
  default 0.82) — não hardcoded no código, ajustável sem novo deploy de lógica.
- PNG nunca é convertido pra JPEG nesta spec (preserva transparência) — só
  redimensionado quando acima do limite.

## Fora de escopo nesta spec

- Reprocessar imagens já existentes no bucket — só uploads novos a partir desta
  mudança (mesma decisão já registrada na issue #22).
- Geração de múltiplos tamanhos (srcset) no backend — responsabilidade do
  `next/image`/Vercel do lado do `vitrio-web`, já em uso.
- Compressão real de PNG (quantização de paleta, ex. pngquant) — PNG só ganha
  redimensionamento nesta spec; comprimir de verdade fica pra uma spec futura, se o
  peso de logos PNG se mostrar um problema real na prática.
- Conversão de formato (ex. JPEG/PNG → WebP) no upload — fora do MVP, mesma decisão
  de escopo da spec 003 quanto a formatos aceitos.
