---
status: accepted
---

# Cadastro público mantido aberto, apesar do plano original de "só admin provisiona"

A decisão de produto original (herdada do projeto de referência `wacatolog`) era que
revendedoras nunca se auto-cadastram — só o admin provisiona contas. Revertemos isso:
`POST /register` fica aberto pra qualquer um com o link, e toda conta criada via
registro público recebe o papel `RESELLER`. `ADMIN` nunca é alcançável via cadastro —
existe só via seed manual no banco.

Esse é um risco deliberado e aceito: o projeto é pessoal e não divulgado (o único
usuário real é a avó do mantenedor), então a exposição de um endpoint de registro
aberto é julgada aceitável em troca de não precisar construir um fluxo/UI de
provisionamento por admin. Revisitar se o produto for aberto ao público algum dia.
