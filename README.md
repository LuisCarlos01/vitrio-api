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

## Deployment

AWS EC2 (`t3.micro`), HTTPS via Elastic IP + `sslip.io` + Caddy, CI/CD via GitHub Actions
(ver [ADR-0005](docs/adr/0005-aws-ec2-deploy-with-https-and-cicd.md) e o runbook em
[`docs/deployment.md`](docs/deployment.md)). Preencher aqui o hostname real após o primeiro
provisionamento:

- API: `https://{PLACEHOLDER}.sslip.io`
- Swagger UI: `https://{PLACEHOLDER}.sslip.io/swagger-ui.html`
