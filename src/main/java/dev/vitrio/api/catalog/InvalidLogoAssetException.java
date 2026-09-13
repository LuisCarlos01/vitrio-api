package dev.vitrio.api.catalog;

/** Lançada quando {@code logoAssetId} não existe ou pertence a um catálogo diferente (spec 007, US1 cenário 2). */
public class InvalidLogoAssetException extends RuntimeException {

    public InvalidLogoAssetException() {
        super("logoAssetId must reference an existing Asset in this catalog");
    }
}
