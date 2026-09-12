package dev.vitrio.api.product;

/** Lançada quando o catálogo já tem 50 produtos e uma criação é tentada (spec 004, US2 cenário 3, `ideia.md`). */
public class ProductLimitExceededException extends RuntimeException {

    public ProductLimitExceededException() {
        super("Catalog has reached the maximum of 50 products");
    }
}
