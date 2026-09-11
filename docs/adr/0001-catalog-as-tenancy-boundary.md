---
status: accepted
---

# `Catalog`, e não `Reseller`, é a fronteira de tenancy

Uma `Reseller` pode ter vários `Catalog`s (ex.: um por marca que revende), e cada
`Catalog` é totalmente isolado — produtos, categorias, banners e configuração de
WhatsApp nunca são compartilhados, nem entre catálogos da mesma revendedora.
Escolhemos `Catalog` como unidade de tenancy em vez de `Reseller` porque as linhas de
negócio separadas de uma revendedora (Natura vs. semijoias artesanais, por exemplo)
precisam de identidade visual, lista de produtos e número de WhatsApp independentes,
sem nenhum vazamento entre elas.

A posse é uma foreign key direta (`Catalog.ownerId` → `User`), não uma tabela de
membership (`user_id` ↔ `catalog_id` ↔ role), porque o MVP não tem conceito de
colaboradores ou múltiplos usuários por catálogo. Revisitar com uma tabela de
membership se/quando uma revendedora precisar compartilhar acesso ao catálogo com uma
ajudante.

## Opções consideradas

- `Reseller` como tenant, um catálogo por revendedora (mais simples, mas não atende
  ao requisito confirmado de múltiplos catálogos independentes por revendedora).
- Tabela de membership para posse (igual ao projeto de referência `wacatolog`, mas
  adiciona uma entidade de junção sem caso de uso atual).
