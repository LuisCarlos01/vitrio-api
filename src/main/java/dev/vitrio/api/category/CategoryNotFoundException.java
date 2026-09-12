package dev.vitrio.api.category;

/**
 * Lançada tanto para um {@code id} inexistente quanto para uma categoria de outro catálogo — a
 * mesma exceção para os dois casos garante o 404 genérico (ADR-0003), mesmo padrão de
 * {@code CatalogNotFoundException}.
 */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException() {
        super("Category not found");
    }
}
