# Spec 006: Importação de produtos via CSV

**Status**: draft

**Contexto**: sexta spec do Vitrio, cobre a jornada "Importar catálogo" do `ideia.md`.
Continua sobre `Catalog`/`Product`/`Asset` das specs 003 e 004: cada linha válida do
CSV vira um `Product` de verdade, com imagem baixada da URL informada e reidratada no
storage próprio (mesmo pipeline de validação/upload da spec 003 — nunca um link
externo é salvo direto no banco). Termos seguem [`CONTEXT.md`](../../CONTEXT.md).

**Decisão de escopo**: importação via CSV só (`ideia.md`) — PDF fica pra uma versão
futura. Sem edição de linha antes de confirmar: o fluxo é "revisar o preview → decidir
se confirma o arquivo inteiro", não um editor de planilha. Confirmação é **síncrona**
(a resposta HTTP só volta quando todas as linhas já foram processadas) — o limite de
50 produtos por catálogo mantém o pior caso gerenciável; um job assíncrono com
polling adicionaria um conceito de estado persistido que a spec não precisa.

## User Stories

### US1 — Pré-visualizar um CSV antes de importar (P1)

Como `Reseller` autenticada, quero enviar um CSV e ver uma prévia do que vai ser
criado antes de confirmar, para revisar erros sem já ter poluído meu catálogo.

**Critério de pronto**: `POST /api/v1/catalogs/{catalogId}/products/import/preview`
recebe o arquivo, valida linha a linha (sem persistir nada) e devolve, pra cada
linha, seu número, os valores das colunas (já normalizados) e se seria criada ou
recusada (com o motivo).

**Cenários de aceite**:
1. **Dado** um CSV com cabeçalho `nome,codigo,descricao,imagem` (UTF-8, delimitador
   vírgula, aspas conforme RFC 4180 pra campos com vírgula/quebra de linha
   embutida), **quando** enviado, **então** cada linha é validada e a prévia retorna
   uma lista com número da linha, os valores das colunas e o resultado
   (válida/erro + motivo).
2. **Dado** um cabeçalho com variação de maiúscula/minúscula ou espaços em volta
   (ex.: ` Nome `, `CODIGO`), **quando** enviado, **então** é reconhecido normalmente
   — nomes de coluna são comparados ignorando caixa e espaços nas pontas.
3. **Dado** o valor de qualquer célula com espaços nas pontas, **quando** validado,
   **então** os espaços são removidos antes de qualquer verificação (obrigatoriedade,
   formato de URL, duplicidade de SKU).
4. **Dado** uma linha sem `nome` (vazio depois do trim), **quando** pré-visualizada,
   **então** aparece com erro, sem bloquear a prévia das demais linhas.
5. **Dado** uma linha com `imagem` vazia ou com um valor que não é uma URL
   `http(s)://` bem formada, **quando** pré-visualizada, **então** aparece com erro —
   imagem é obrigatória pro `Product` (spec 004, `ideia.md`).
6. **Dado** um `codigo` (SKU) que já existe em outro produto do mesmo catálogo, ou que
   se repete em outra linha do mesmo CSV, **quando** pré-visualizado, **então**
   **todas** as linhas com esse SKU (inclusive a primeira ocorrência, se a
   duplicidade for interna ao arquivo) aparecem com erro de SKU duplicado — nenhuma
   delas "vence" silenciosamente (spec 004: SKU é único por catálogo quando
   preenchido, comparação exata, sensível a maiúsculas/minúsculas).
7. **Dado** um catálogo que já tem produtos, **quando** o total (existentes + linhas
   válidas do CSV) ultrapassaria 50, **então** as linhas que excederem o limite
   aparecem com erro de limite atingido — as primeiras até completar 50 continuam
   válidas.
8. **Dado** uma linha com menos colunas do que o cabeçalho (faltando vírgulas
   finais), **quando** validada, **então** as colunas ausentes são tratadas como
   vazias e seguem as regras normais (ex.: `imagem` ausente vira o mesmo erro de
   "imagem obrigatória") — não existe um erro genérico separado de "linha
   malformada".
9. **Dado** uma coluna `preco`/`price` (ou qualquer coluna fora de
   `nome,codigo,descricao,imagem`) presente no CSV, **quando** pré-visualizado ou
   importado, **então** ela é sempre ignorada — preço nunca é lido (`ideia.md`,
   `Product` nunca tem preço).
10. **Dado** um arquivo maior que 2MB ou com mais de 500 linhas, **quando** enviado,
    **então** é recusado inteiro antes de processar qualquer linha (proteção contra
    arquivo abusivo, independente do limite de 50 produtos).
11. **Dado** um catálogo que não é meu, **quando** tento pré-visualizar um CSV nele,
    **então** recebo 404 genérico (mesmo padrão de isolamento, ADR-0003).
12. A prévia **não baixa as imagens nem cria nada** — só valida nome, formato de URL,
    duplicidade de SKU e o limite de 50. O download/upload de imagem só acontece na
    confirmação (US2); uma linha pode passar na prévia e ainda assim falhar na
    confirmação, se a URL da imagem deixar de responder ou não for uma imagem válida
    nesse meio-tempo.

### US2 — Confirmar a importação (P1)

Como `Reseller` autenticada, quero confirmar a importação depois de revisar a prévia,
para criar de fato os produtos válidos.

**Critério de pronto**: `POST /api/v1/catalogs/{catalogId}/products/import/confirm`
recebe o mesmo CSV, executa a mesma validação estrutural da prévia e, pra cada linha
que passar, baixa a imagem, envia pro S3 (mesma validação de formato/tamanho da spec
003) e cria o `Product`. Linhas inválidas (na validação estrutural ou no download da
imagem) são reportadas, sem impedir a criação das demais. Resposta síncrona: só volta
depois que todas as linhas foram processadas.

**Cenários de aceite**:
1. **Dado** um CSV só com linhas válidas, **quando** confirmado, **então** todos os
   produtos são criados com `isVisible=false`, `isOrderable=false`, `isActive=true`,
   `quantityAvailable=0` (mesmo default conservador da criação manual, spec 004, US2)
   — a revendedora revisa e ativa manualmente depois.
2. **Dado** um CSV com linhas válidas e inválidas misturadas, **quando** confirmado,
   **então** as válidas são criadas normalmente e a resposta reporta, por linha, se
   foi criada (com o `id` do `Product`) ou recusada (com o motivo) — a confirmação
   nunca é tudo-ou-nada.
3. **Dado** uma URL de imagem que responde, mas cujo conteúdo real (bytes) não é
   JPEG nem PNG, **quando** confirmado, **então** essa linha falha com o mesmo erro
   de formato não suportado da spec 003 — nunca confia na extensão do link nem no
   `Content-Type` declarado pelo servidor de origem.
4. **Dado** uma imagem baixada maior que 10MB, **quando** confirmado, **então** essa
   linha falha com o mesmo erro de tamanho da spec 003, antes de qualquer upload pro
   S3 — a conexão é encerrada assim que o corpo ultrapassa 10MB, mesmo que o
   `Content-Length` declarado minta.
5. **Dado** uma URL de imagem que não responde dentro de 5s (conexão) ou 10s
   (leitura), **quando** confirmado, **então** essa linha falha com uma mensagem
   genérica de "não foi possível obter a imagem dessa URL" — sem detalhar o motivo
   real (timeout, DNS, host bloqueado etc.) na resposta, pra não abrir uma forma de
   mapear a rede interna por tentativa e erro; o motivo específico fica só em log de
   servidor.
6. **Dado** uma URL de imagem cujo host resolve pra um IP privado, loopback,
   link-local ou de metadados de nuvem (ex.: `169.254.169.254`) — direto ou depois de
   um redirect HTTP —, **quando** confirmado, **então** o download nunca acontece, e
   essa linha falha com a **mesma mensagem genérica** do cenário anterior (sem
   distinguir "bloqueado por segurança" de "não respondeu").
7. **Dado** um `codigo` (SKU) preenchido numa linha válida, **quando** o produto é
   criado, **então** o `sku` do `Product` é esse valor (já sem espaços nas pontas);
   linhas sem `codigo` criam produto com `sku=null` (spec 004: SKU é opcional).
8. **Dado** um catálogo que não é meu, **quando** tento confirmar uma importação
   nele, **então** recebo 404 genérico.
9. **Dado** mais de 10 confirmações da mesma `Reseller` numa janela de 1 hora,
   **quando** a 11ª é tentada, **então** é recusada por rate limit — mesmo padrão de
   bucket já usado em login/registro (spec 001), limite generoso o bastante pro uso
   legítimo (ninguém reimporta o catálogo inteiro repetidamente).

## Regras de negócio

- Formato do arquivo: CSV completo (RFC 4180 — aspas, vírgula/quebra de linha
  embutida em campo), cabeçalho obrigatório, UTF-8, delimitador vírgula (`ideia.md`).
  Nome de coluna comparado ignorando maiúscula/minúscula e espaços nas pontas.
  Colunas reconhecidas: `nome` (obrigatória), `codigo` (opcional, vira `sku`),
  `descricao` (opcional), `imagem` (obrigatória, URL `http(s)://`). Qualquer outra
  coluna (ex.: `preco`) é ignorada, nunca lida. Valor de toda célula tem espaços nas
  pontas removidos antes de qualquer validação. Coluna ausente ao final de uma linha
  conta como valor vazio, sem erro distinto de "linha malformada".
- Limite estrutural do arquivo: recusado inteiro (antes de processar qualquer linha)
  se ultrapassar 2MB ou 500 linhas — independente do limite de 50 produtos por
  catálogo (spec 004), que continua sendo checado linha a linha.
- Toda linha importada gera um `Product` com `categoryId=null` — o CSV não atribui
  categoria; a revendedora categoriza manualmente depois, se quiser (`PATCH`, spec
  004 US3).
- Imagem: a URL é baixada pelo backend e tratada como um upload comum (spec 003) —
  mesmas regras (JPEG/PNG por conteúdo real dos bytes, até 10MB, `storageKey` gerado
  pelo sistema). O link externo em si nunca é persistido; só a URL final do `Asset`
  no S3. Timeout de 5s pra conectar e 10s pra ler a resposta.
- **Proteção contra SSRF**: o backend nunca segue a URL informada às cegas. Só
  esquemas `http`/`https` são aceitos; antes de cada conexão (incluindo depois de
  cada redirect HTTP seguido) o host é resolvido e o IP é checado contra uma lista de
  bloqueio — privado, loopback, link-local, metadados de nuvem — nunca uma allowlist
  de domínios (restringiria demais, já que a imagem pode estar hospedada em qualquer
  lugar). Toda falha de download (timeout, DNS, formato inválido, bloqueio de SSRF)
  vira a mesma mensagem genérica de erro na resposta — nunca diferenciada, pra não
  virar oráculo de reconhecimento de rede interna.
- SKU único por catálogo quando preenchido, comparação exata sensível a
  maiúsculas/minúsculas — mesma regra da spec 004, sem exceção pro CSV. Duplicidade
  (contra produtos existentes, ou entre linhas do mesmo arquivo) é erro de linha em
  **todas** as linhas envolvidas, nunca só nas repetições subsequentes.
- Limite de 50 produtos por catálogo (spec 004) é checado também na importação,
  contando produtos já existentes — linhas que excederiam o limite falham
  individualmente, sem bloquear as que cabem.
- Preview e confirmação rodam a mesma validação estrutural (nome, formato de URL,
  duplicidade de SKU, limite, tamanho/linhas do arquivo); a diferença é que só a
  confirmação baixa a imagem de verdade e persiste. Não há armazenamento de um
  "rascunho de importação" entre os dois passos — a confirmação reenvia o mesmo
  arquivo.
- Resposta de preview e confirmação, por linha: número da linha, valores das colunas
  já normalizados (trim aplicado), e resultado — válida ou erro com motivo (na
  confirmação, linha válida também traz o `id` do `Product` criado).
- Rate limit dedicado na confirmação: 10 por hora por `Reseller` (bucket próprio,
  mesmo padrão de `LoginRateLimitFilter`/`RegisterRateLimitFilter`, spec 001). Sem
  rate limit dedicado na prévia (não persiste nem baixa imagem, custo baixo).
- Sem detecção de duplicidade de conteúdo além do SKU (ex.: mesmo nome duas vezes não
  é erro) — `ideia.md`: "resolver duplicidade é fluxo manual de edição, fora do
  import".
- Isolamento: mesmo padrão das specs anteriores — catálogo validado contra o dono
  autenticado, 404 genérico (nunca 403) pra catálogo que não é meu (ADR-0003).

## Fora de escopo nesta spec

- Importação via PDF (`pdfjs-dist`) — versão futura (`ideia.md`).
- Edição de linha durante a prévia (ex.: corrigir um nome direto na tela de preview)
  — o fluxo é só ver erros e decidir confirmar ou reenviar um CSV corrigido.
- Atribuir categoria durante a importação.
- Detecção/sinalização de duplicidade por nome ou por SKU-quase-igual (fuzzy) — só a
  duplicidade exata de SKU já coberta pela regra de unicidade existente.
- Histórico de importações / reimportação recorrente.
- Confirmação assíncrona (job/polling) — decisão explícita acima, revisitar só se o
  volume real de produtos por catálogo crescer muito além de 50.
- Qualquer normalização de imagem (HEIC/HEIF, WebP) — mesma decisão de escopo da spec
  003, formatos aceitos continuam só JPEG/PNG.
