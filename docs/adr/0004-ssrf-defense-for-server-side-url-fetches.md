---
status: accepted
---

# Defesa contra SSRF: blocklist de IP com revalidação por redirect, erro sempre genérico

A spec 006 (importação de produtos via CSV) introduz o primeiro ponto do sistema em
que o backend busca uma URL fornecida pelo usuário (a coluna `imagem` do CSV,
baixada e reidratada como `Asset`, spec 003). Qualquer feature futura parecida (ex.
um hero banner referenciado por URL) provavelmente vai reusar o mesmo mecanismo, o
que torna esta decisão um precedente, não só um detalhe local da spec 006.

Decisão: só esquemas `http`/`https` são aceitos; antes de cada conexão — incluindo
depois de cada redirect HTTP seguido — o host é resolvido e o IP resultante é
checado contra uma lista de bloqueio (privado, loopback, link-local, metadados de
nuvem como `169.254.169.254`). Redirects são seguidos (não bloqueados de cara), mas
cada salto é revalidado antes de conectar, fechando o TOCTOU de um servidor
malicioso que redireciona pra um IP interno só depois do primeiro contato. Toda
falha de download — timeout, DNS, formato de imagem inválido, ou bloqueio de SSRF —
retorna a mesma mensagem genérica ao cliente; o motivo específico só aparece em log
de servidor.

Alternativas consideradas: uma allowlist de domínios conhecidos seria mais segura,
mas foi descartada — a imagem de um produto pode estar hospedada em qualquer lugar
(Google Drive, Imgur, CDN de uma loja antiga), e prever essa lista de antemão não é
viável sem inviabilizar o caso de uso legítimo. Mensagens de erro diferenciadas
("host bloqueado" vs. "não respondeu") foram descartadas porque dariam a um atacante
um oráculo pra mapear a rede interna por tentativa e erro.

Custo aceito: blocklist de IP é mais permissiva que allowlist — qualquer host
público, mesmo desconhecido, é aceito. Revisitar se o modelo de ameaça mudar (ex. se
o Vitrio passar a hospedar dados sensíveis o suficiente pra justificar restringir a
origens conhecidas) ou se um incidente real mostrar que a blocklist não é
suficiente.
