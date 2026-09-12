package dev.vitrio.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Vitrine pública de uma revendedora. {@code ownerId} é uma FK direta para {@code users.id} — sem
 * tabela de membership, porque o MVP não tem conceito de colaborador por catálogo (ADR-0001).
 * {@code slug} é único globalmente e, nesta spec, imutável após a criação.
 */
@Entity
@Table(name = "catalogs")
public class Catalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, updatable = false)
    private String slug;

    // Nullable de propósito: "nunca customizou" fica distinguível de "customizou igual ao
    // default" — o valor padrão é resolvido em CatalogColorDefaults, não aqui (spec 002).
    @Column(name = "primary_color_hex")
    private String primaryColorHex;

    @Column(name = "button_color_hex")
    private String buttonColorHex;

    @Column(name = "instagram_handle")
    private String instagramHandle;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "whatsapp_verification_status", nullable = false)
    private WhatsappVerificationStatus whatsappVerificationStatus = WhatsappVerificationStatus.UNVERIFIED;

    @Column(name = "whatsapp_verified_at")
    private Instant whatsappVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Catalog() {
        // Construtor exigido pelo JPA.
    }

    public Catalog(UUID ownerId, String name, String slug) {
        this.ownerId = ownerId;
        this.name = name;
        this.slug = slug;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getPrimaryColorHex() {
        return primaryColorHex;
    }

    public String getButtonColorHex() {
        return buttonColorHex;
    }

    public String getInstagramHandle() {
        return instagramHandle;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public WhatsappVerificationStatus getWhatsappVerificationStatus() {
        return whatsappVerificationStatus;
    }

    public Instant getWhatsappVerifiedAt() {
        return whatsappVerifiedAt;
    }

    // Cada parâmetro null significa "não veio no PATCH" (US1) — mantém o valor atual em vez de
    // apagar; a validação de formato (regex de cor, tamanho) já aconteceu na camada de DTO.
    public void applyPersonalization(String name, String primaryColorHex, String buttonColorHex, String instagramHandle) {
        if (name != null) {
            this.name = name;
        }
        if (primaryColorHex != null) {
            this.primaryColorHex = primaryColorHex;
        }
        if (buttonColorHex != null) {
            this.buttonColorHex = buttonColorHex;
        }
        if (instagramHandle != null) {
            this.instagramHandle = instagramHandle;
        }
    }

    // Qualquer troca de número reseta a verificação, mesmo reenviando o número idêntico (US2,
    // cenário 3) — por isso não há um "if (!Objects.equals(...))" aqui, é sempre incondicional.
    public void updateWhatsappNumber(String normalizedNumber) {
        this.whatsappNumber = normalizedNumber;
        this.whatsappVerificationStatus = WhatsappVerificationStatus.UNVERIFIED;
        this.whatsappVerifiedAt = null;
    }

    public void verifyWhatsapp(Instant verifiedAt) {
        this.whatsappVerificationStatus = WhatsappVerificationStatus.VERIFIED;
        this.whatsappVerifiedAt = verifiedAt;
    }
}
