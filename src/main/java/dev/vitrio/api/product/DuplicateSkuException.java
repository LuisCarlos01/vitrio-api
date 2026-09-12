package dev.vitrio.api.product;

/** Lançada quando {@code sku} já está em uso por outro produto do mesmo catálogo (spec 004, US2 cenário 4). */
public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException() {
        super("SKU already in use in this catalog");
    }
}
