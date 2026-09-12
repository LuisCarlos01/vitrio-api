package dev.vitrio.api.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Imagem enviada por uma {@code Reseller}, sempre vinculada a um único {@code Catalog}
 * (CONTEXT.md) — {@code storageKey} é gerado pelo sistema, nunca o nome de arquivo original
 * (evita colisão e vazamento de nome de arquivo do usuário, spec 003).
 */
@Entity
@Table(name = "assets")
public class Asset {

    // Sem @GeneratedValue: o id precisa ser conhecido *antes* do save, pra compor o
    // storageKey (spec 003: "{catalogId}/{assetId}.{extensão}") — gerado em AssetService e
    // atribuído aqui, diferente do Catalog (gerado pelo banco), que não tem essa necessidade.
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "catalog_id", nullable = false, updatable = false)
    private UUID catalogId;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;

    @Column(name = "public_url", nullable = false, updatable = false)
    private String publicUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Asset() {
        // Construtor exigido pelo JPA.
    }

    public Asset(UUID id, UUID catalogId, String storageKey, String contentType, long byteSize, String publicUrl) {
        this.id = id;
        this.catalogId = catalogId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.byteSize = byteSize;
        this.publicUrl = publicUrl;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCatalogId() {
        return catalogId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
