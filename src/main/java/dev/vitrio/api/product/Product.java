package dev.vitrio.api.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Item cadastrado dentro de um {@code Catalog} (CONTEXT.md). {@code imageAssetId} é obrigatório
 * (ideia.md) — todo produto exige uma imagem já enviada (spec 003). {@code categoryId} é
 * opcional; ambos sempre validados contra o mesmo {@code catalogId} do produto (spec 004).
 * Nasce com {@code isVisible}/{@code isOrderable} desligados e {@code isActive} ligado — a
 * revendedora revisa e ativa manualmente (spec 004, US2).
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "catalog_id", nullable = false, updatable = false)
    private UUID catalogId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sku")
    private String sku;

    @Column(name = "description")
    private String description;

    @Column(name = "image_asset_id", nullable = false)
    private UUID imageAssetId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable = 0;

    @Column(name = "is_visible", nullable = false)
    private boolean visible = false;

    @Column(name = "is_orderable", nullable = false)
    private boolean orderable = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // Construtor exigido pelo JPA.
    }

    public Product(UUID catalogId, String name, String sku, String description, UUID imageAssetId, UUID categoryId) {
        this.catalogId = catalogId;
        this.name = name;
        this.sku = sku;
        this.description = description;
        this.imageAssetId = imageAssetId;
        this.categoryId = categoryId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatalogId() {
        return catalogId;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public UUID getImageAssetId() {
        return imageAssetId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public int getQuantityAvailable() {
        return quantityAvailable;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isOrderable() {
        return orderable;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Cada parâmetro null significa "não veio no PATCH" (spec 004, US3) — mantém o valor atual
    // em vez de apagar; validação de unicidade/pertencimento (sku, imageAssetId, categoryId) já
    // aconteceu em ProductService antes de chamar isto. isVisible/isOrderable/isActive são
    // flags independentes entre si (CONTEXT.md, "Estados do produto": isVisible é "independente
    // de estoque ou ativação") — desativar não força os outros dois pra false, e reativar não
    // os restaura automaticamente (US3 cenário 3): eles só mudam quando explicitamente enviados
    // no PATCH, nunca como efeito colateral de isActive. A composição pra exibição pública
    // (isActive && isVisible, ideia.md) é responsabilidade de quem lê, não de quem grava aqui.
    public void applyUpdate(
            String name,
            String sku,
            String description,
            UUID imageAssetId,
            UUID categoryId,
            Integer quantityAvailable,
            Boolean isVisible,
            Boolean isOrderable,
            Boolean isActive) {
        if (name != null) {
            this.name = name;
        }
        if (sku != null) {
            this.sku = sku;
        }
        if (description != null) {
            this.description = description;
        }
        if (imageAssetId != null) {
            this.imageAssetId = imageAssetId;
        }
        if (categoryId != null) {
            this.categoryId = categoryId;
        }
        if (quantityAvailable != null) {
            this.quantityAvailable = quantityAvailable;
        }
        if (isVisible != null) {
            this.visible = isVisible;
        }
        if (isOrderable != null) {
            this.orderable = isOrderable;
        }
        if (isActive != null) {
            this.active = isActive;
        }
    }
}
