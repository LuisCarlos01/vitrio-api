# Deploy — vitrio-api

Runbook do deploy, conforme decidido em [ADR-0005](adr/0005-aws-ec2-deploy-with-https-and-cicd.md):
AWS EC2 `t3.micro`, HTTPS via Elastic IP + `sslip.io` + Caddy, CI/CD via GitHub Actions desde o
primeiro deploy. O provisionamento da instância em si (console AWS) é manual por natureza — só o
deploy contínuo é automatizado.

Este documento é o passo a passo para **quem for provisionar a instância pela primeira vez** —
não é uma automação, é a sequência de comandos/cliques a seguir.

## 1. Provisionar a instância EC2

No Console AWS:

1. **EC2 → Launch Instance**.
2. **AMI**: Amazon Linux 2023 (free tier eligible).
3. **Instance type**: `t3.micro`.
4. **Key pair**: crie um par novo (ex.: `vitrio-api-key`) e baixe o `.pem` — é a única forma de
   acessar a instância via SSH depois (sem senha).
5. **Network settings / Security group**: crie um novo grupo com estas regras de entrada:
   - `SSH` (porta 22) — origem `0.0.0.0/0` (autenticação é só por chave).
   - `HTTP` (porta 80) — origem `0.0.0.0/0`.
   - `HTTPS` (porta 443) — origem `0.0.0.0/0` (necessária para o Caddy/Let's Encrypt).
6. **Storage**: 20 GB `gp3`.
7. **Launch instance**. Anote o **IP público** exibido depois que a instância entrar em estado
   `running`.
8. **Elastic IP**: aloque um Elastic IP (`EC2 → Network & Security → Elastic IPs → Allocate`) e
   associe-o a esta instância. Sem isso, o IP público muda a cada `stop`/`start` (seção "Depois de
   validado" abaixo) e quebraria o hostname `sslip.io` calculado a partir dele. Um Elastic IP é
   gratuito **enquanto associado a uma instância em execução** — nunca deixe um alocado e sem
   associar, custa por hora.

## 2. Conectar via SSH e instalar Docker

```bash
chmod 400 vitrio-api-key.pem
ssh -i vitrio-api-key.pem ec2-user@<IP-PÚBLICO>
```

Na instância (Amazon Linux 2023 já vem com `dnf`):

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# Disconecte e reconecte via SSH para o grupo `docker` ter efeito na sessão.
```

Instale os plugins `docker compose` e `docker buildx` (Amazon Linux 2023 não traz nenhum dos dois
por padrão via `dnf`; o pacote `docker` do repositório só traz o Engine). `compose build`/
`up --build` exige `buildx ≥ 0.17.0`:

```bash
DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
mkdir -p $DOCKER_CONFIG/cli-plugins

curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o $DOCKER_CONFIG/cli-plugins/docker-compose
chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
docker compose version

curl -SL https://github.com/docker/buildx/releases/latest/download/buildx-v0.37.0.linux-amd64 \
  -o $DOCKER_CONFIG/cli-plugins/docker-buildx
chmod +x $DOCKER_CONFIG/cli-plugins/docker-buildx
docker buildx version
```

> A versão do `buildx` acima é só a mais recente confirmada ao escrever este runbook — confirme
> se ainda é a atual em `github.com/docker/buildx/releases/latest` antes de rodar.

## 3. Clonar o repositório e configurar o `.env`

```bash
git clone https://github.com/LuisCarlos01/vitrio-api.git
cd vitrio-api
cp .env.example .env
```

Edite o `.env` na instância (`nano .env` ou similar) com valores reais de produção:

- `JWT_SIGNING_KEY`: gere um valor real, diferente do default de desenvolvimento (ex.:
  `openssl rand -base64 48`).
- `POSTGRES_PASSWORD`: troque pelo valor real de produção.
- `VITRIO_CORS_ALLOWED_ORIGINS`: origem(ns) do frontend implantado (`vitrio-web`).
- `AWS_S3_BUCKET_NAME` / `AWS_S3_REGION` / `AWS_S3_ACCESS_KEY_ID` / `AWS_S3_SECRET_ACCESS_KEY`:
  as mesmas credenciais reais do bucket S3 já usado pelo upload de imagem (spec 003) — não são
  específicas de produção, mas idem, nunca commitadas.

Esse `.env` fica **só na instância** — nunca é commitado (`.gitignore`).

## 4. Configurar HTTPS (Caddy + sslip.io) — ADR-0005

Calcule o hostname a partir do Elastic IP alocado no passo 8 da seção 1: troque os pontos por
hifens e acrescente `.sslip.io` (ex.: `18.117.253.110` → `18-117-253-110.sslip.io`). `sslip.io`
não exige cadastro nem configuração própria — o nome já resolve para o IP embutido nele.

Na instância, copie o template committed e preencha o hostname real:

```bash
cp Caddyfile.example Caddyfile
# edite Caddyfile e troque {PLACEHOLDER} pelo hostname calculado acima
```

`Caddyfile` **não é commitado** — é específico desta instância.

## 5. Subir a aplicação

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

O overlay `docker-compose.prod.yml` (committed) adiciona o serviço `caddy`, que expõe `80`/`443`,
faz proxy para `app:8080` e obtém/renova automaticamente um certificado Let's Encrypt (desafio
HTTP-01) para o hostname configurado no `Caddyfile`.

Acompanhe os logs até confirmar que a aplicação subiu e as migrations Flyway rodaram:

```bash
docker compose logs -f app
docker compose logs -f caddy
```

## 6. Validar o deploy

Do seu próprio computador (substitua `<HOSTNAME>` pelo hostname `sslip.io` calculado no passo 4):

```bash
curl -I http://<HOSTNAME> # espera redirect (30x) para https
curl -v https://<HOSTNAME>/actuator/health # espera cadeia de certificado válida, sem -k
curl https://<HOSTNAME>/swagger-ui.html -I
curl -X POST https://<HOSTNAME>/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-test@example.com","password":"Str0ngP@ssw0rd!"}'
curl -X POST https://<HOSTNAME>/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-test@example.com","password":"Str0ngP@ssw0rd!"}'
```

Um `login` bem-sucedido retorna `accessToken`/`refreshToken`. Valide também, com um cliente que
preserve cookies, que o cookie `Secure` do refresh token é retido entre chamadas (só funciona sob
HTTPS de verdade, que é exatamente o que esta configuração provê).

## 7. Depois de validado

- Atualize a seção "Deployment" do `README.md` com o hostname `sslip.io` real.
- Pare a instância (`EC2 → Stop instance`, não `Terminate`) quando não estiver testando
  ativamente — reduz o custo a quase zero fora dos períodos de uso.

## 8. Deploy automatizado (CI/CD)

Depois do provisionamento (passos 1–7 acima), toda atualização é automatizada via GitHub Actions:
`.github/workflows/ci.yml` roda `./mvnw clean verify` a cada push/PR em `main`;
`.github/workflows/deploy.yml` dispara só depois que `CI` termina com sucesso em `main`, conecta
via SSH na instância e roda o mesmo `git pull && docker compose -f docker-compose.yml -f
docker-compose.prod.yml up --build -d` do passo 5 — sem registry de imagem (Docker Hub/ECR), a
EC2 continua buildando a própria imagem. Depois de subir, o workflow espera até 60s pelo `app`
responder em `http://localhost:8080/actuator/health` (direto no container, sem passar pelo
Caddy) — se não responder, o job falha e mostra os últimos logs do container.

**Secrets necessários** (`Settings → Secrets and variables → Actions` do repositório no GitHub):

| Secret | Valor |
|---|---|
| `DEPLOY_HOST` | IP público (ou Elastic IP) da instância |
| `DEPLOY_SSH_USER` | `ec2-user` |
| `DEPLOY_SSH_KEY` | Conteúdo do `.pem` gerado no passo 1 (chave privada completa) |
| `PROD_JWT_SIGNING_KEY` | O mesmo valor real de produção usado no `.env` da instância |
| `PROD_POSTGRES_PASSWORD` | O mesmo valor real de produção usado no `.env` da instância |
| `VITRIO_CORS_ALLOWED_ORIGINS` | Origens permitidas por CORS, separadas por vírgula |
| `PROD_AWS_S3_BUCKET_NAME` | Bucket S3 real de produção |
| `PROD_AWS_S3_REGION` | Região do bucket |
| `PROD_AWS_S3_ACCESS_KEY_ID` | Chave de acesso IAM escopada ao bucket |
| `PROD_AWS_S3_SECRET_ACCESS_KEY` | Chave secreta correspondente |

O workflow **reescreve o `.env` da instância a cada deploy** a partir desses secrets — não
depende de um `.env` manual criado no passo 3 continuar existindo por conta própria.

O caminho manual (passos 1–7) continua funcionando normalmente como fallback.

## Limitações conhecidas

- **Sem alta disponibilidade** — instância única; reiniciar o host derruba a aplicação até subir
  de novo manualmente.
- **Custo não é zero para sempre** — ver ADR-0005 para o modelo de crédito da AWS e a estimativa
  de custo pós-crédito.
- **Hostname não é um domínio próprio** — `sslip.io` é funcional, não é uma marca memorável.
- **Build na própria instância é sensível a memória** — a `t3.micro` só tem 1GB de RAM; o
  workflow de deploy já para o container antigo antes do build para evitar concorrência de
  memória entre builder e app (ver comentário em `deploy.yml`).
