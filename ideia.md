# Vitrio — Catálogo da Revendedora

## Problema
Revendedora (Natura, Avon, Boticário, semijoias, etc.) hoje depende de revista física,
PDF gigante no WhatsApp ou fotos soltas. Cliente precisa garimpar produto em dezenas de
páginas. Atrito nos dois lados.

## Para quem
- Primeira usuária real: a avó (revendedora).
- Mercado potencial: qualquer revendedor(a) que hoje vive de Instagram + WhatsApp + PDF
  (Natura, Avon, Eudora, Mary Kay, roupas, semijoias, lingerie, cosméticos, doces,
  artesanato, importados).

## Hipótese central
Dar à revendedora uma vitrine própria (link único por catálogo, tipo
`vitrio.com/maria-boticario`), fácil de atualizar e fácil de compartilhar no WhatsApp —
substituindo PDF/revista sem virar e-commerce completo.

## Conceito / papéis
- **Admin** (o dev): único, criado via seed manual no banco (nunca via cadastro
  público). Gerencia planos, importações, templates, erros, catálogos globais — escopo
  amplo é pós-MVP; no MVP não tem dashboard próprio.
- **Revendedora**: cria a própria conta (cadastro público aberto), e a partir daí cria e
  gerencia **N catálogos** próprios, cada um independente. Por catálogo: personaliza
  identidade visual (nome, WhatsApp, Instagram, cores, banner), importa produtos,
  edita/oculta produtos, cria categorias, compartilha o link.
- **Cliente final**: não cria conta. Acessa o link público de um catálogo,
  navega/busca/filtra, e finaliza com botão "Tenho interesse" → abre WhatsApp com
  mensagem pré-formatada (produto, sem preço).

## Conceito central: Catálogo é a fronteira de tenancy (não a revendedora)
Uma revendedora pode ter vários catálogos (ex.: um por marca que revende — "Catálogo -
Boticário", "Catálogo - Semijoias"), cada um com link público próprio e totalmente
isolado dos outros: produtos, categorias, banners e configuração de WhatsApp não são
compartilhados entre catálogos da mesma revendedora, mesmo que o valor (ex.: número de
WhatsApp) se repita entre eles. "Loja/marca" não é uma entidade gerenciada pelo
sistema — é só um campo de texto livre no catálogo.

## Fluxo principal (3 jornadas do MVP)
1. **Importar catálogo**: upload de CSV (nome, código/SKU opcional, descrição opcional,
   URL de imagem) → sistema baixa e reidrata a imagem no storage próprio → preview →
   confirmar cria só produtos novos, sempre com `isVisible=false`, `isOrderable=false`,
   `quantityAvailable=0` (revendedora ajusta manualmente depois de revisar). PDF fica
   para uma versão futura.
2. **Personalizar catálogo**: nome do catálogo, identidade visual (cor primária + cor
   do botão, com seletor de cor e campo hex), banner inicial em carrossel (até 5
   imagens), WhatsApp (com verificação própria do catálogo), Instagram. Suporte a modo
   claro/escuro desde o MVP (fundo fixo por modo — cinza claro no claro, quase
   preto/sépia no escuro; cores de identidade personalizáveis nos dois modos).
3. **Compartilhar no WhatsApp**: link público do catálogo, carrinho temporário 100%
   client-side (sem backend, sem estoque reservado, sem preço na mensagem) → `wa.me`
   com mensagem pré-preenchida.

Esse trio é o coração do produto — se funcionar bem, já valida a ideia.

**Decisão de repositório/stack**: novo repositório, construído do zero, backend em
Java/Spring Boot (não Next.js+Supabase). O `wacatolog` **não** é a base de código — é
só fonte de referência pra acelerar (specs, regras de negócio, modelo de dados,
decisões de produto já validadas), reimplementadas na stack nova. O `sentinel-auth-api`
(projeto educacional próprio) é reaproveitado como base do módulo de autenticação.

## Decisões de escopo já tomadas
- **Import é via CSV no MVP** (não PDF). Colunas: `nome` (obrigatório), `codigo`/`sku`
  (opcional), `descricao` (opcional), `imagem` (URL, baixada e reidratada no storage
  próprio — não referencia URL externa direto). Cabeçalho obrigatório, UTF-8,
  delimitador vírgula. Sem preço (nunca lido, mesmo se a coluna existir). Sem detecção
  de duplicidade por SKU nesta entrega — código repetido apenas cria normalmente.
  Import via PDF (com `pdfjs-dist`, extração de texto, preview) fica para versão
  futura.
- **Sem e-commerce, mas com carrinho temporário**: cliente monta carrinho no frontend
  (`localStorage`, sem endpoint de carrinho na API), pode esvaziar/revisar, nada é
  persistido nem reserva estoque. Confirmar abre WhatsApp com mensagem pré-preenchida
  **sem preço** (saudação, nome do catálogo, produtos, SKU quando houver, quantidades,
  total, pedido de confirmação).
- **Multi-tenant desde o início**: catálogo é a fronteira de tenancy (ver seção
  acima); isolamento garantido na camada de aplicação (toda query filtra por
  `catalogId` validado contra o dono autenticado no service layer) — sem RLS no
  Postgres, já que não usamos Supabase.
- **Cadastro público permitido**: `POST /register` fica aberto — qualquer um com o
  link pode criar conta. Risco aceito conscientemente (projeto pessoal, não
  divulgado). Toda conta criada via registro público nasce com papel `RESELLER`;
  `ADMIN` só existe via seed manual no banco.
- **`Catalog.ownerId`**: FK direto para `User`, sem tabela de membership — só o dono
  autenticado tem acesso ao catálogo (sem colaboradores/multiusuário no MVP).
- **Sem microserviços/monorepo**: monolito modular Spring Boot + frontend separado,
  dois repositórios (API e web).

## Modelo de dados (conceito herdado do `wacatolog`, adaptado: Store → Catalog,
## reimplementado em JPA/Postgres)
```
User (role ADMIN | RESELLER) -> Catalog (ownerId, FK direta, sem membership)
Catalog -> Asset (imagens normalizadas, storage próprio — provedor a definir)
Catalog -> Category (nome; produto referencia 0 ou 1)
Catalog -> Product (name, sku?, description, imageAssetId, categoryId?,
                     quantityAvailable, isVisible, isOrderable, isActive)
Catalog -> HeroBanner (image, título/texto opcional, position 1–5, isActive)
```
Regras centrais: produto público exige `isActive && isVisible`; adicionável ao
carrinho exige também `isOrderable` e `quantityAvailable > 0`; SKU único por catálogo
quando preenchido; asset só pode pertencer a produtos/banners do mesmo catálogo;
máximo de 50 produtos por catálogo.

## Regras de negócio que dão substância ao projeto (não é CRUD puro)
- Produto não visível não aparece, independente de disponibilidade.
- Produto visível e indisponível pode ser consultado, mas não entra no carrinho.
- Produto desativado não aparece e não pode ser pedido (desativar preserva o
  cadastro); exclusão permanente é ação distinta, com diálogo de confirmação
  explícito ("não pode ser desfeita") — aplica a produto e a catálogo inteiro.
- Reativação não restaura `is_visible`/`is_orderable` automaticamente.
- Importação de CSV cria só produtos novos; código/SKU já existente não é sinalizado
  nesta entrega — resolver duplicidade é fluxo manual de edição, fora do import.
- Envio ao WhatsApp exige número validado e testado **por catálogo** — mesmo que o
  mesmo número seja usado em outro catálogo da mesma revendedora, a verificação não é
  compartilhada, cada catálogo confirma separadamente. Enquanto não verificado, envio
  fica indisponível e o carrinho é preservado.
- Slug do catálogo é único globalmente (define a URL pública) e não é
  credencial/prova de autorização.

## Stack (decidida: do zero, Java/Spring, reaproveitando `sentinel-auth-api`)
- Backend: Java 25, Spring Boot 4.1.0, Spring Security, Maven, JPA, Flyway,
  PostgreSQL, Argon2id (hash de senha) — mesmas versões/ferramentas do
  `sentinel-auth-api`, reaproveitado como base do módulo de autenticação (JWT access
  15min HS256 + refresh opaco 7 dias rotativo com cookie httpOnly).
- Storage de imagens: provedor ainda em aberto (ver Perguntas abertas) — MinIO local
  via Docker em dev, provedor free tier a definir em produção.
- Formatos de imagem aceitos: JPEG/PNG/WebP/HEIC/HEIF, normalizados no upload.
- Frontend: Next.js, React, TypeScript, Tailwind — repositório separado, hospedado na
  Vercel, consumindo a API REST do backend.
- Dois repositórios: API (Java/Spring) e web (Next.js). Sem monorepo, sem
  microserviços.

## MVP (escopo equivalente às specs 001–006 do `wacatolog`, a reimplementar,
## adaptado ao modelo Catalog multi-por-revendedora)
Cadastro público de revendedora + criação self-service de catálogo · CRUD de produtos
com regras de visibilidade/estoque · catálogo público por slug com busca/carrinho
temporário · importação via CSV com preview e revisão · configuração e verificação de
WhatsApp por catálogo · hero banners (carrossel, até 5) · modo claro/escuro.

## Roadmap pós-MVP
- Favoritos, promoções, produtos em destaque.
- Importações recorrentes e histórico de importação.
- Importação via PDF (extração com `pdfjs-dist`).
- Detecção de duplicidade por SKU/código no import.
- Analytics (visitas, produtos vistos, cliques no WhatsApp).
- IA para interpretar catálogos com mais robustez (PDFs escaneados, layouts
  variados).
- Templates de identidade visual por catálogo.
- Dashboard de Admin dedicado (hoje é só seed manual + acesso direto ao banco).

## O que já sabemos que NÃO é
- Não é "sistema para cadastrar produtos" (genérico demais) — é "transforme o catálogo
  que você já recebe em vitrine personalizada para seus clientes".
- Não é e-commerce completo: carrinho existe, mas não persiste pedido, não reserva
  estoque, não tem checkout/pagamento/frete/nota fiscal.
- Não é importação via PDF no MVP — decisão invertida em relação ao `wacatolog`: CSV
  primeiro, PDF fica pra depois.
- Não é microserviços, nem monorepo.
- Não tem preço na mensagem de WhatsApp (decisão deliberada, evita atrito com preço
  desatualizado).
- Não usa `tone`/`shape` como atributos de produto (decorativos na referência
  `catalogo-edne-codex`, descartados — produto tem foto real, não precisa preencher
  ausência de imagem).

## Prototipos existentes (só referência — repo novo, código do zero)

### `wacatolog/` — fonte de referência de produto/specs, não de código
Specs formais (001–006), PRD, ADRs, data-model, testes unitários/E2E já resolvidos.
Não será a base de código (stack diferente: lá é Next.js+Supabase, aqui será
Java/Spring), mas as decisões de produto e regras de negócio documentadas nele
(flags `isVisible/isOrderable/isActive`, verificação de WhatsApp, hero banners,
diálogo de exclusão permanente, formatos de imagem aceitos) valem como especificação
a reimplementar — adaptando `Store` para `Catalog` (fronteira de tenancy mudou de
"loja" para "catálogo", com N catálogos por revendedora). O fluxo de import via PDF
(spec 004) fica como referência para quando essa funcionalidade entrar no roadmap.

### `catalogo-edne-codex/` — referência pontual de UI
Site estático de vitrine única, dados hardcoded em `data/products.ts`. Sem
arquitetura reaproveitável. Atributos `tone`/`shape` confirmados como decorativos
(nunca usados para filtro/busca) — descartados do modelo de produto do Vitrio.
Componentes de card/listagem/busca por texto podem inspirar a UI do frontend novo.

### `sentinel-auth-api/` — base do módulo de autenticação
Projeto educacional próprio (Java 25, Spring Boot 4.1.0, Maven, Flyway). Mecanismo de
JWT/refresh token é reaproveitado como está. É single-tenant (só `User`+`Role` flat,
sem conceito de organização) — o Vitrio adiciona `Catalog.ownerId` por cima. Cadastro
público já existe lá e é mantido aberto no Vitrio (decisão consciente, ver acima).

## Infraestrutura (decidido)
- **Storage de imagens**: AWS S3 (free tier), aproveitando crédito já disponível na
  conta AWS do mantenedor.
- **Backend**: nova instância EC2 dedicada ao `vitrio-api`, separada da instância que
  já roda o `sentinel-auth-api` (não é reaproveitada/derrubada — os dois projetos
  continuam no ar em paralelo). Atenção: o free tier de EC2 (750h/mês) é por conta,
  não por instância — rodando duas instâncias simultâneas, o limite grátis se esgota
  mais rápido; verificar saldo restante no console da AWS antes de assumir que as
  duas ficam sem custo.
- Frontend já definido na Vercel (decisão anterior, sem mudança).

## Próximo passo
Criar os repositórios `vitrio-api` (Java/Spring, reaproveitando `sentinel-auth-api`
como base) e `vitrio-web` (Next.js) e começar pela spec/modelo de dados equivalente à
001 do `wacatolog` (provisionamento de conta), adaptada: cadastro público de
revendedora + criação self-service de catálogo, em vez de provisionamento pelo admin.
