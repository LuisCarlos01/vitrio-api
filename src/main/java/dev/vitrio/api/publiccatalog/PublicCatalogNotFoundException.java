package dev.vitrio.api.publiccatalog;

/** Lançada quando o {@code slug} não corresponde a nenhum catálogo (spec 005, US1 cenário 7). */
public class PublicCatalogNotFoundException extends RuntimeException {

    public PublicCatalogNotFoundException() {
        super("Catalog not found");
    }
}
