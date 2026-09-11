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

## Agent skills

### Issue tracker

GitHub (via `gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context (`CONTEXT.md` + `docs/adr/` at repo root). See `docs/agents/domain.md`.
