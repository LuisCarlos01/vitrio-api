# Spec 010: Sinalizar customização de cor do catálogo

**Status**: draft

**Contexto**: décima spec do Vitrio. Complementa a [spec 002](../002-catalog-personalization/spec.md)
(US1, cenário de aceite 2), que resolve `primaryColorHex`/`buttonColorHex` pro
placeholder neutro (`#6D28D9`/`#059669`, `CatalogColorDefaults`) quando o `Catalog`
não tem cor definida, e nunca retorna `null` pra esses campos. Termos seguem
[`CONTEXT.md`](../../CONTEXT.md).

**Problema**: o `vitrio-web` (handoff de 2026-09-15, PRs #43/#44/#45) reportou que o
contrato atual não permite distinguir "revendedora nunca escolheu cor" de
"revendedora escolheu, por coincidência, o hex exato do placeholder via seletor
livre". As duas situações produzem a mesma resposta (`#6D28D9`/`#059669`), e nenhuma
heurística no frontend baseada só no valor do hex consegue separá-las — é limitação
de informação exposta pelo contrato, não de implementação do cliente. Mitigação
atual do `vitrio-web` (tratar esse par exato como "sem cor") cobre o caso comum mas
tem bug conhecido no caso raro (reload reverte silenciosamente a escolha real pro
tema padrão).

**Decisão**: manter o comportamento da spec 002 intacto (placeholder resolvido,
nunca `null` — cenário de aceite 2 continua valendo) e adicionar um campo novo,
derivado do estado real de customização, não do valor do hex.

## User Stories

### US1 — Expor se a cor do catálogo foi customizada (P1)

Como cliente da API (`vitrio-web`), quero saber se a cor do catálogo foi
efetivamente escolhida pela revendedora ou é o placeholder de aplicação, para
decidir corretamente qual paleta/aviso de contraste mostrar no formulário, sem
depender do valor do hex.

**Critério de pronto**: `CatalogResponse` (e qualquer resposta que exponha a
identidade visual do catálogo — hoje `PublicCatalogResponse` também) ganha o campo
`hasCustomColor: boolean`, verdadeiro sse pelo menos um `PATCH` já definiu
`primaryColorHex` ou `buttonColorHex` explicitamente (isto é, `Catalog` tem valor
não nulo armazenado pra pelo menos um dos dois campos).

**Cenários de aceite**:
1. **Dado** um catálogo recém-criado, sem nenhum `PATCH` de cor, **quando** consulto
   o catálogo, **então** `hasCustomColor` é `false` e `primaryColorHex`/
   `buttonColorHex` continuam retornando o placeholder (spec 002, US1, cenário 2 —
   inalterado).
2. **Dado** um catálogo em que a revendedora já enviou `primaryColorHex` ou
   `buttonColorHex` via `PATCH` — qualquer valor válido, incluindo coincidir por
   acaso com o placeholder —, **quando** consulto o catálogo, **então**
   `hasCustomColor` é `true`.
3. **Dado** um catálogo com `hasCustomColor: true`, **quando** a mesma cor é
   reenviada num `PATCH` seguinte (mesmo valor), **então** `hasCustomColor`
   permanece `true` (não existe caminho de "desfazer" customização — nenhum
   endpoint remove cor, mesma linha da spec 002 pro WhatsApp).
4. O campo é somente leitura: não existe input `hasCustomColor` no corpo do
   `PATCH` — é sempre derivado do estado interno do `Catalog`.

## Regras de negócio

- `hasCustomColor` é calculado, não persistido como coluna nova: `catalog.getPrimaryColorHex() != null || catalog.getButtonColorHex() != null`,
  avaliado no mesmo ponto onde `CatalogColorDefaults` resolve os placeholders hoje.
- Nenhuma migração de schema é necessária — `Catalog` já guarda `null` internamente
  antes do primeiro `PATCH` de cor (comportamento pré-existente da spec 002; só a
  resolução pro placeholder acontecia depois, ao montar a resposta).
- `CatalogColorDefaults.resolvePrimary`/`resolveButton` continuam com a mesma
  assinatura e comportamento — não é uma trava dessa spec, mas devem permanecer os
  únicos pontos de resolução do placeholder pra não haver dois códigos decidindo a
  mesma coisa.

## Fora de escopo nesta spec

- Diferenciar `primaryColorHex` customizado de `buttonColorHex` customizado
  independentemente — um único `hasCustomColor` cobre os dois campos juntos, já que
  o formulário do frontend (handoff) trata cor primária e cor do botão como uma
  decisão só. Se o frontend precisar de granularidade por campo, é uma spec futura.
- Qualquer mudança em `PublicCatalogResponse` além de replicar o mesmo campo — a
  vitrine pública não tem hoje um caso de uso que dependa disso, mas o campo é
  exposto lá também por consistência de contrato entre as duas respostas.
