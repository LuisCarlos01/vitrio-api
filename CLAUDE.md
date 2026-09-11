## Documentação

Toda documentação deste repositório (`CONTEXT.md`, ADRs em `docs/adr/`, `docs/agents/*.md`,
`README.md`, etc.) é escrita em PT-BR. Nomes de entidades/termos técnicos (ex.: `Catalog`,
`Product`) permanecem em inglês dentro do texto, como identificadores.

## Antes de commitar

Toda implementação passa por `code-review` (skill) antes do commit, usando a
documentação do repo como fonte da verdade — não um review solto: comparar o código
contra a spec correspondente em `specs/`, as ADRs em `docs/adr/`, `CONTEXT.md` e este
`CLAUDE.md`. Isso vale mesmo quando o código é copiado/adaptado de outro projeto de
referência (ex.: `sentinel-auth-api`): comentários, Javadocs e referências a ADRs
herdados do projeto de origem devem ser revisados e ajustados à realidade deste repo
antes do commit — nunca citar uma ADR, issue ou versão que não existe neste
repositório.

## Fluxo de uma spec

Ordem fixa, nesta sequência: `spec.md` em `specs/` → `/to-tickets` quebra a spec em
issues **antes de qualquer código ser escrito** → implementação → `code-review`
(seção "Antes de commitar") → commit → fechar cada issue referenciando o commit que a
entregou. Tickets não nascem retroativos — a etapa de `/to-tickets` acontece logo
após a spec ser aprovada, não depois da implementação já pronta.

## Tickets

Tickets (`to-tickets`) são sempre publicados como Issues nativas do GitHub — nunca como
arquivos locais em `.scratch/` — usando as dependências nativas de bloqueio do GitHub
entre elas (não uma lista numerada em texto). Toda issue criada por `to-tickets` também
entra num GitHub Project deste repositório.

Título, corpo, labels e milestones de issues/PRs são em PT-BR (ver `docs/agents/
triage-labels.md` para os labels de triage). Exceção: **tópicos do repositório**
(GitHub topics, ex. `java`, `spring-boot`, `postgresql`) ficam em inglês — são
taxonomia técnica padrão do GitHub usada pra descoberta entre repositórios, traduzir
prejudica isso sem ganho real.

Toda issue criada neste repositório é atribuída a `LuisCarlos01` (único mantenedor).

## Agent skills

### Issue tracker

GitHub (via `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context (`CONTEXT.md` + `docs/adr/` at repo root). See `docs/agents/domain.md`.
