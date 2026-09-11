# Spec 002: Personalização do catálogo (identidade e contato)

**Status**: draft

**Contexto**: segunda spec do Vitrio, cobre a jornada "Personalizar catálogo" do
`ideia.md`. Continuação da [spec 001](../001-account-catalog-provisioning/spec.md):
uma `Reseller` já autenticada edita a identidade e o contato do seu próprio
`Catalog`. Termos seguem [`CONTEXT.md`](../../CONTEXT.md).

**Fora de escopo, deliberadamente**: hero banners (carrossel de imagens) — depende de
upload de imagem, e o provedor de storage (AWS S3) ainda não está implementado neste
repo. Fica pra uma spec seguinte, quando o módulo de upload existir. Modo claro/escuro
é comportamento 100% de frontend (fundo fixo por tema) — nada aqui no backend além dos
campos de cor, que já são tema-agnósticos.

## User Stories

### US1 — Editar identidade do catálogo (P1)

Como `Reseller` autenticada, quero editar o nome, a cor primária, a cor do botão e o
Instagram do meu catálogo, para personalizar a vitrine do jeito que eu quiser.

**Critério de pronto**: `PATCH /api/v1/catalogs/{id}` atualiza só os campos enviados,
recusa acesso a catálogo de outra revendedora com o mesmo padrão 404 genérico da
spec 001.

**Cenários de aceite**:
1. **Dado** um catálogo existente meu, **quando** envio `{ "name": "Catálogo -
   Avon" }`, **então** só o nome muda; cor primária, cor do botão, Instagram e slug
   permanecem como estavam (slug nunca recalcula, mesma regra da spec 001).
2. **Dado** um catálogo meu sem cor customizada, **quando** consulto o catálogo,
   **então** `primaryColorHex`/`buttonColorHex` retornam os valores padrão da
   aplicação (nunca `null`).
3. **Dado** um valor de cor enviado no `PATCH`, **quando** não bate com o formato
   `#RRGGBB` (6 dígitos hexadecimais, com `#`), **então** a requisição é recusada por
   validação, sem alterar nenhum campo.
4. **Dado** qualquer valor de cor válido no formato `#RRGGBB` (não há paleta fechada
   — o frontend usa um seletor livre tipo color picker), **quando** enviado,
   **então** é aceito e persistido, qualquer que seja a cor escolhida.
5. **Dado** um `instagramHandle`, **quando** enviado, **então** é salvo como texto
   livre, sem validação de formato além de tamanho máximo razoável (ex.: 255
   caracteres) — não valida se é uma conta real nem se começa com `@`.
6. **Dado** o catálogo de outra revendedora, **quando** tento fazer `PATCH` nele pelo
   `id`, **então** recebo 404 genérico (nunca 403), mesmo padrão de isolamento da
   spec 001 (US4).

### US2 — Configurar WhatsApp do catálogo (P1)

Como `Reseller` autenticada, quero configurar o número de WhatsApp do meu catálogo,
para que meus clientes consigam me chamar.

**Critério de pronto**: `PUT /api/v1/catalogs/{id}/whatsapp` normaliza e salva o
número; qualquer alteração bem-sucedida reseta a verificação para não-verificado.

**Cenários de aceite**:
1. **Dado** um número em formato brasileiro comum (com ou sem `+55`, com símbolos —
   ex. `(11) 91234-5678`), **quando** enviado, **então** é normalizado pro formato
   internacional (`55` + DDD + número, só dígitos) antes de salvar.
2. **Dado** um valor que não corresponde a um número de WhatsApp brasileiro válido
   depois de normalizado, **quando** enviado, **então** a requisição é recusada com
   mensagem clara, e o número anterior (se houver) permanece inalterado.
3. **Dado** um catálogo com WhatsApp já verificado, **quando** a revendedora salva
   qualquer número novo (mesmo que seja **o mesmo número de novo**), **então** o
   status volta pra não-verificado e a data de verificação é limpa — sem exceção.
4. Não existe endpoint pra remover/limpar o número — só substituir por outro.

### US3 — Verificar WhatsApp do catálogo (P1)

Como `Reseller` autenticada, quero confirmar que o número configurado é meu de
verdade, pra habilitar o botão de WhatsApp na vitrine pública.

**Critério de pronto**: `POST /api/v1/catalogs/{id}/whatsapp/verify` marca o número
atual como verificado.

**Cenários de aceite**:
1. **Dado** um catálogo com número configurado e ainda não verificado, **quando** a
   revendedora confirma a verificação, **então** o status muda pra verificado e a
   data de verificação é registrada.
2. **Dado** um catálogo sem nenhum número configurado, **quando** tento verificar,
   **então** a requisição é recusada com mensagem clara.
3. **Dado** o catálogo de outra revendedora, **quando** tento verificar o WhatsApp
   dele, **então** recebo 404 genérico (mesmo padrão de isolamento).
4. Este endpoint só marca o estado como verificado — **não** integra com nenhuma API
   real do WhatsApp; a confirmação é inteiramente manual por parte da revendedora
   (mesma decisão do `wacatolog`, spec 005 dele).

## Regras de negócio

- `Catalog` ganha os campos: `primaryColorHex` (default de aplicação se ausente),
  `buttonColorHex` (default de aplicação se ausente), `instagramHandle` (opcional,
  texto livre), `whatsappNumber` (opcional, normalizado), `whatsappVerificationStatus`
  (`UNVERIFIED` | `VERIFIED`, default `UNVERIFIED`), `whatsappVerifiedAt` (opcional).
- Formato de cor: regex `^#[0-9A-Fa-f]{6}$`. Sem paleta fechada — qualquer cor válida
  nesse formato é aceita.
- WhatsApp: normalização e validação seguem o mesmo formato usado pelo `wacatolog`
  (`^55[0-9]{10,11}$` após normalização). Qualquer alteração bem-sucedida do número
  reseta a verificação, mesmo reenviando o número idêntico ao já salvo.
- Verificação é manual (a revendedora confirma que reconhece o número), sem chamada a
  API externa do WhatsApp — igual à decisão já validada no `wacatolog`.
- Isolamento: todos os endpoints desta spec seguem o mesmo padrão da spec 001 —
  filtro por `ownerId` na camada de serviço, 404 genérico (nunca 403) pra catálogo de
  outra revendedora ou inexistente (ADR-0003).

## Fora de escopo nesta spec

- Hero banners / carrossel de imagens — depende do módulo de upload (AWS S3), que
  ainda não existe neste repo. Spec seguinte.
- Exposição do WhatsApp/dados no catálogo público (endpoint público por slug) — ainda
  não especificado; fica para quando a jornada "Compartilhar no WhatsApp" for
  detalhada.
- Exclusão de catálogo — spec seguinte, junto com o diálogo de confirmação de
  exclusão permanente (mesmo padrão de produto da spec 001).
- Qualquer editor visual de tema além dos dois campos de cor (ex.: fontes, layout) —
  fora do MVP.
