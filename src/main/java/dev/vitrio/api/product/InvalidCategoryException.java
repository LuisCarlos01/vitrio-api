package dev.vitrio.api.product;

/** Lançada quando {@code categoryId} não existe ou pertence a um catálogo diferente do produto (spec 004, US2 cenário 5). */
public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException() {
        super("categoryId must reference an existing Category in this catalog");
    }
}
