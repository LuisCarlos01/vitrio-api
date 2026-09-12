---
status: accepted
---

# Deploy em EC2 (AWS) com HTTPS via Elastic IP + `sslip.io` + Caddy, CI/CD desde o início

O backend do Vitrio (specs 001–006) cobre a jornada principal do MVP descrita em
`ideia.md` — cadastro, catálogo, personalização/WhatsApp, upload de imagem,
produto/categoria, vitrine pública e import CSV — e precisa estar acessível fora do
ambiente local do autor para validar contra um frontend real (`vitrio-web`) e permitir
demonstração. Decisão modelada diretamente sobre `sentinel-auth-api`, projeto de
referência deste repositório (`CLAUDE.md`), que já passou por esse mesmo problema em
duas ADRs separadas (`0011-aws-ec2-manual-deploy.md` e
`0013-https-via-elastic-ip-sslip-caddy.md`): lá a decisão nasceu em duas etapas (deploy
manual em HTTP puro primeiro, HTTPS depois, motivado por um bug real de cookie
`Secure` descartado). Aqui não há esse histórico — a decisão já nasce completa, sem
uma fase intermediária sem HTTPS.

Deploy em uma instância **EC2 `t3.micro`** (AWS, mesma região e modelo de custo do
projeto de referência: crédito de até US$ 200 por até 6 meses, depois ~US$ 9–10/mês
se mantida ligada 24/7 — mitigado parando a instância quando ociosa), rodando o
`docker-compose.yml` já existente **sem mudança de arquitetura**: API e Postgres no
mesmo host. Um overlay `docker-compose.prod.yml` (committed) acrescenta o serviço
`caddy` como reverse proxy, expondo `80`/`443` e obtendo/renovando automaticamente um
certificado Let's Encrypt via desafio HTTP-01. O hostname usado é um Elastic IP
(gratuito enquanto associado a uma instância em execução, resolve a instabilidade do
IP público entre `stop`/`start`) convertido para um nome `sslip.io` (ex.
`18-117-253-110.sslip.io`) — sem custo, sem cadastro, sem comprar domínio próprio.
`Caddyfile` (com o hostname real) e `.env` (segredos reais) ficam só na instância,
nunca commitados — `docs/adr/0004` já estabelece esse padrão de não persistir segredo
em texto, e aqui se estende ao `.env` de produção, que agora também carrega as
credenciais reais do bucket S3 (`AWS_S3_*`, já existentes desde a spec 003) além de
Postgres/JWT/CORS.

Diferente do projeto de referência, aqui **CI e deploy automatizado nascem juntos**,
não como um ticket futuro: um workflow `CI` (GitHub Actions) roda `./mvnw clean
verify` a cada push/PR em `main`; um workflow `Deploy` dispara só depois que `CI`
termina com sucesso em `main` (`workflow_run`), conecta via SSH na instância e refaz
`git pull` + `docker compose -f docker-compose.yml -f docker-compose.prod.yml up
--build -d`, checando o health check antes de reportar sucesso. Não há motivação
registrada aqui (como havia no projeto de referência) para justificar uma fase
manual-antes-de-automatizada — o primeiro provisionamento da instância em si (console
AWS: lançar instância, key pair, security group, Elastic IP) continua manual por
natureza, só o deploy contínuo é automatizado desde o primeiro dia.

## Opções consideradas

- **ECS Fargate / App Runner / outro PaaS gerenciado**: mais simples de operar, mas
  descartado — este repositório já segue o modelo operacional do projeto de
  referência (EC2 auto-operado), e trocar de modelo agora duplicaria decisões já
  validadas lá sem ganho concreto para o estágio atual do projeto.
- **RDS gerenciado para o Postgres**: descartado por ora pelo mesmo motivo de custo
  do projeto de referência — não é gratuito de forma indefinida, e o
  `docker-compose.yml` já resolve isso sem custo adicional.
- **Comprar domínio próprio / Cloudflare**: descartado — seria custo monetário novo
  sem necessidade real neste estágio; `sslip.io` resolve HTTPS funcional sem essa
  exigência.
- **Deploy manual antes de automatizar**: descartado — sem uma motivação de
  aprendizado explícita para essa fase intermediária (diferente do projeto de
  referência), automatizar desde o início evita o retrabalho de migrar do manual para
  CI/CD depois.
