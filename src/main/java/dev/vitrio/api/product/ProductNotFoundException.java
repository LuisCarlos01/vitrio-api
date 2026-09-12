package dev.vitrio.api.product;

/**
 * Lançada tanto para um {@code id} inexistente quanto para um produto de outro catálogo — a
 * mesma exceção para os dois casos garante o 404 genérico (ADR-0003).
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super("Product not found");
    }
}
