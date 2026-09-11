# Vitrio

Plataforma que dá a revendedoras (Natura, Avon, Boticário, semijoias etc.) uma vitrine
pública própria para substituir catálogo em PDF/revista física, sem virar e-commerce
completo (sem checkout, pagamento ou estoque reservado).

## Language

### Papéis

**Admin**:
Papel único do sistema, provisionado apenas via seed manual no banco — nunca nasce de
cadastro público. Gerencia o sistema globalmente (fora do escopo do MVP).
_Avoid_: Maintainer, Superuser

**Reseller**:
Usuária que revende produtos de terceiros e cria um ou mais `Catalog` próprios depois
de se cadastrar (cadastro público). Toda conta criada via registro público nasce com
esse papel.
_Avoid_: Revendedor(a) genérico como nome de entidade, Store owner, Seller

**Customer**:
Visitante anônimo de um `Catalog` público. Não tem conta, nunca autentica; monta um
carrinho temporário no navegador e finaliza o interesse via WhatsApp.
_Avoid_: User (não é uma conta do sistema), Shopper, Buyer

### Catálogo e conteúdo

**Catalog**:
A fronteira de tenancy do sistema: vitrine pública com link/slug próprio, produtos,
categorias, banners e configuração de WhatsApp isolados de qualquer outro catálogo —
inclusive de outros catálogos da mesma `Reseller`. Uma `Reseller` pode ter N catálogos
(ex.: um por marca que revende).
_Avoid_: Store, Loja, Vitrine (como nome de entidade)

**Brand** (marca/loja de origem):
Texto livre informado pela `Reseller` num `Catalog` (ex.: "Natura", "Boticário",
"Semijoias") — não é uma entidade gerida pelo sistema, nem tem cadastro ou regras
próprias.
_Avoid_: Store (confunde com `Catalog`)

**Product**:
Item cadastrado dentro de um `Catalog`: nome, SKU opcional, descrição, imagem e três
flags independentes (ver "Estados do produto"). Nunca tem preço.
_Avoid_: Item, SKU (como nome da entidade inteira)

**Category**:
Agrupamento opcional de `Product` dentro de um `Catalog`, criado livremente pela
`Reseller`. Um produto referencia no máximo uma categoria.

**HeroBanner**:
Imagem do carrossel de destaque no topo do `Catalog` público, com posição (1 a 5,
máximo 5 por catálogo) e estado ativo/inativo.
_Avoid_: Banner sozinho (ambíguo), Slide

**Asset**:
Referência normalizada de uma imagem (de `Product` ou `HeroBanner`) no storage,
sempre vinculada a um único `Catalog` — nunca compartilhada entre catálogos.

**Slug**:
Identificador textual único globalmente que define a URL pública de um `Catalog`.
Não é credencial nem prova de autorização — só endereçamento.

### Estados do produto

**isVisible**:
Controla se o `Product` aparece no `Catalog` público. Independente de estoque ou
ativação.

**isOrderable**:
Controla se o `Product`, já visível, pode entrar no carrinho do `Customer`.
Independente de `isVisible` — um produto pode ser visível e não pedível.

**isActive**:
Controla se o cadastro do `Product` está desativado. Desativar oculta e bloqueia
pedido, mas preserva o registro (diferente de exclusão permanente, que remove o
cadastro). Reativar não restaura `isVisible`/`isOrderable` automaticamente.
