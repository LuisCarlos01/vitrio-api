package dev.vitrio.api.asset;

/**
 * Lançada tanto para um {@code id} inexistente quanto para um asset de outro catálogo — a
 * mesma exceção para os dois casos garante o 404 genérico (ADR-0003), mesmo padrão de
 * {@code ProductNotFoundException}/{@code CategoryNotFoundException}.
 */
public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException() {
        super("Asset not found");
    }
}
