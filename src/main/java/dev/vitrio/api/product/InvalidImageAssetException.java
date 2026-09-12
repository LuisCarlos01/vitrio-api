package dev.vitrio.api.product;

/** Lançada quando {@code imageAssetId} não existe ou pertence a um catálogo diferente do produto (spec 004, US2 cenário 2). */
public class InvalidImageAssetException extends RuntimeException {

    public InvalidImageAssetException() {
        super("imageAssetId must reference an existing Asset in this catalog");
    }
}
