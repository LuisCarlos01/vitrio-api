# Spec 009: Histórico de importação CSV

**Status**: draft

**Contexto**: nona spec do Vitrio. Bloqueia a issue #26 do `vitrio-web` (overview do
dashboard, card "última importação") — o fluxo de import (spec 006) hoje é só
preview→confirm, sem persistir nenhum registro do que aconteceu; não existe de onde
a API expor "quando foi a última importação" porque esse dado nunca foi guardado.
Continua sobre `CsvImportService.confirm()` (spec 006). Termos seguem
[`CONTEXT.md`](../../CONTEXT.md).

**Decisão de escopo**: registro agregado por confirmação (quando, quantas linhas
aceitas/recusadas), não um histórico linha a linha (isso já existe na resposta de
`POST .../import/confirm` no momento da chamada, spec 006 — não persistido, mas
também não é o que a issue #26 pede: só "última importação" resumida). Se um
histórico completo/paginado for necessário depois, é spec futura.

## User Stories

### US1 — Registrar cada confirmação de importação (P1)

Como sistema, quero registrar automaticamente cada confirmação de importação CSV,
para o dashboard poder mostrar quando foi a última.

**Critério de pronto**: toda chamada bem-sucedida a
`POST /api/v1/catalogs/{catalogId}/products/import/confirm` persiste um registro
com `catalogId`, data/hora e contagem de linhas aceitas/recusadas.

**Cenários de aceite**:
1. **Dado** uma confirmação de importação processada (mesmo que todas as linhas
   sejam recusadas), **quando** ela termina, **então** um registro é persistido com
   `catalogId`, timestamp da confirmação, quantidade de linhas aceitas
   (`productId != null`) e quantidade de linhas recusadas.
2. **Dado** múltiplas confirmações no mesmo catálogo, **quando** cada uma termina,
   **então** cada uma vira um registro novo — nunca sobrescreve o anterior (base pra
   "última importação" ser sempre a mais recente por data).
3. O registro é criado **mesmo que 0 produtos sejam aceitos** — "última importação"
   inclui tentativas totalmente malsucedidas, não só sucessos.

### US2 — Consultar a última importação de um catálogo (P1)

Como `Reseller` autenticada, quero consultar quando foi e como foi minha última
importação de CSV, para o dashboard mostrar isso sem eu precisar lembrar.

**Critério de pronto**: `GET /api/v1/catalogs/{catalogId}/imports/latest` retorna o
registro mais recente daquele catálogo, ou `204 No Content` se nunca houve
importação.

**Cenários de aceite**:
1. **Dado** um catálogo meu com ao menos uma importação confirmada, **quando**
   consulto o endpoint, **então** recebo o registro mais recente (por timestamp),
   com contagens de aceitos/recusados.
2. **Dado** um catálogo meu que nunca teve importação, **quando** consulto o
   endpoint, **então** recebo `204 No Content` — não é um erro, é um estado válido
   ("nunca importou"), então não é 404 nem um corpo com campos nulos.
3. **Dado** o catálogo de outra revendedora, **quando** tento consultar o histórico
   dele, **então** recebo 404 genérico (nunca 403), mesmo padrão de isolamento das
   specs anteriores (ADR-0003).
4. Múltiplas importações no mesmo catálogo: o endpoint sempre retorna só a mais
   recente — não há paginação/lista nesta spec (ver "Fora de escopo").

## Regras de negócio

- Nova entidade `CsvImportLog`: `id`, `catalogId` (FK), `confirmedAt`
  (timestamp da confirmação, não da requisição — mesmo instante em que o
  processamento de todas as linhas terminou), `acceptedCount`, `rejectedCount`.
  Sem referência às linhas individuais nem aos produtos criados (fora de escopo,
  ver acima) — só o agregado.
- Persistido dentro do próprio fluxo de `CsvImportService.confirm()`, depois de
  processar todas as linhas — uma falha ao persistir o log não deve derrubar a
  resposta de confirmação em si (a criação dos produtos já aconteceu; o log é
  auditoria, não path crítico do negócio) — logar o erro, nunca propagar exceção
  que reverta produtos já criados.
- Isolamento: `GET .../imports/latest` segue o mesmo padrão de 404 genérico por
  dono de catálogo já usado em toda leitura/escrita sobre um catálogo específico
  (ADR-0003).

## Fora de escopo nesta spec

- Histórico completo/paginado (lista de todas as importações, não só a última) —
  spec futura, se necessário.
- Detalhe linha a linha do que foi aceito/recusado numa importação passada — já
  existe só na resposta síncrona de `confirm` (spec 006), não persistido aqui.
- Qualquer notificação/alerta sobre importações (ex.: "sua última importação teve
  muitos erros") — puramente dado bruto nesta spec, decisão de UI fica com o
  `vitrio-web`.
