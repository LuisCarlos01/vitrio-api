# Vitrio API

Backend do Vitrio — catálogo digital multi-tenant para revendedoras: cada
revendedora publica um ou mais catálogos próprios e recebe pedidos pelo WhatsApp.

Java 25 / Spring Boot 4.1.0, reaproveitando o módulo de autenticação (JWT +
refresh token, Argon2id) do projeto educacional `sentinel-auth-api` como base.
Contexto de produto em `../ideia.md`, glossário em `CONTEXT.md`, decisões
arquiteturais em `docs/adr/`, spec em implementação em
`specs/001-account-catalog-provisioning/spec.md`.

## Rodando localmente

```
cp .env.example .env
docker compose up
```

Ou sem Docker, com um Postgres local já rodando:

```
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Testes

```
./mvnw clean verify
```

Requer Docker rodando (Testcontainers sobe um Postgres real para os testes de
integração).

Frontend em repositório separado: `vitrio-web`.

## Contrato de API (OpenAPI)

Gerado automaticamente pelo `springdoc-openapi` a partir do código (`OpenApiConfig`,
controllers/DTOs) — não é escrito à mão. Duas formas de consumir:

- **Ao vivo**, contra qualquer instância rodando (local ou implantada):
  `/v3/api-docs` (JSON), `/v3/api-docs.yaml` (YAML) e `/swagger-ui.html` (interativo) —
  todos públicos, sem Access token.
- **Snapshot committed**, para o `vitrio-web` consumir/gerar cliente sem precisar de uma
  instância no ar: [`docs/api/openapi.yaml`](docs/api/openapi.yaml) /
  [`docs/api/openapi.json`](docs/api/openapi.json). É um retrato do momento em que foi
  exportado, **não atualizado automaticamente** — regenerar depois de qualquer mudança de
  contrato (endpoint novo, campo novo/removido em request ou response) relevante para o
  frontend:

  ```
  docker compose up -d postgres
  JWT_SIGNING_KEY=dev-only-signing-key-never-use-in-production-1234567890 \
  AWS_S3_BUCKET_NAME=dummy-bucket AWS_S3_REGION=us-east-1 \
  AWS_S3_ACCESS_KEY_ID=dummy AWS_S3_SECRET_ACCESS_KEY=dummy \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev &

  curl -s http://localhost:8080/v3/api-docs | python3 -m json.tool > docs/api/openapi.json
  curl -s http://localhost:8080/v3/api-docs.yaml > docs/api/openapi.yaml
  ```

  As credenciais AWS/S3 acima são só placeholders para o app subir localmente (o bean do
  S3 não valida conectividade no boot) — não precisam ser reais só para exportar o
  contrato.

## Deployment

AWS EC2 (`t3.micro`), HTTPS via Elastic IP + `sslip.io` + Caddy, CI/CD via GitHub Actions
(ver [ADR-0005](docs/adr/0005-aws-ec2-deploy-with-https-and-cicd.md) e o runbook em
[`docs/deployment.md`](docs/deployment.md)). Preencher aqui o hostname real após o primeiro
provisionamento:

- API: `https://{PLACEHOLDER}.sslip.io`
- Swagger UI: `https://{PLACEHOLDER}.sslip.io/swagger-ui.html`
