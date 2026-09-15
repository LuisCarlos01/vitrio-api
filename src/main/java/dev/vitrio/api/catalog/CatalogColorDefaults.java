package dev.vitrio.api.catalog;

/**
 * Placeholders neutros de marca (spec 002, US1, cenário 2) — nunca expõe {@code null} pro
 * cliente antes da revendedora customizar. Compartilhado por qualquer resposta que exiba a
 * identidade visual do catálogo (privada ou pública, spec 005). Trocar quando o design visual
 * definitivo existir.
 */
public final class CatalogColorDefaults {

    private static final String PRIMARY_COLOR_HEX = "#6D28D9";
    private static final String BUTTON_COLOR_HEX = "#059669";

    private CatalogColorDefaults() {}

    public static String resolvePrimary(Catalog catalog) {
        return catalog.getPrimaryColorHex() != null ? catalog.getPrimaryColorHex() : PRIMARY_COLOR_HEX;
    }

    public static String resolveButton(Catalog catalog) {
        return catalog.getButtonColorHex() != null ? catalog.getButtonColorHex() : BUTTON_COLOR_HEX;
    }

    public static boolean hasCustomColor(Catalog catalog) {
        return catalog.getPrimaryColorHex() != null || catalog.getButtonColorHex() != null;
    }
}
