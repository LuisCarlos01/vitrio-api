---
status: accepted
---

# Isolamento de tenant garantido na camada de aplicação, não via RLS do Postgres

O projeto de referência `wacatolog` (Supabase) usa Row-Level Security do Postgres
como barreira primária de tenancy, com funções `SECURITY DEFINER` para leituras
públicas. O Vitrio roda em Spring Boot + JPA/PostgreSQL puro, sem Supabase. Optamos
por garantir o isolamento de `Catalog` inteiramente na camada de serviço da
aplicação: toda query filtra por `catalogId`, sempre validado contra os catálogos do
usuário autenticado, nunca confiando em input vindo do client.

Isso é mais simples de implementar e testar numa stack Spring pura e é o padrão
comum fora do ecossistema Supabase, ao custo de perder a defesa em profundidade do
RLS (um bug no filtro da camada de serviço não tem barreira de banco como
retaguarda). Revisitar se o modelo de ameaça mudar ou se o time quiser uma segunda
camada de proteção.
