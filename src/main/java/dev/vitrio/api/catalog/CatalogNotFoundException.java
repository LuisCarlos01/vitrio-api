package dev.vitrio.api.catalog;

/**
 * Lançada tanto para um {@code id} inexistente quanto para um catálogo de outro dono — a mesma
 * exceção para os dois casos é o que garante o 404 genérico exigido pela US4 (ADR-0003):
 * o chamador nunca consegue distinguir "não existe" de "não é seu".
 */
public class CatalogNotFoundException extends RuntimeException {

    public CatalogNotFoundException() {
        super("Catalog not found");
    }
}
