package dev.vitrio.api.catalog;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Gera slugs kebab-case a partir do nome do catálogo (spec 001): acentos removidos, minúsculas,
 * sequências de caracteres não alfanuméricos viram um único hífen, sem hífen nas pontas.
 */
final class SlugGenerator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");

    private SlugGenerator() {}

    static String slugify(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        String lowercase = withoutDiacritics.toLowerCase(java.util.Locale.ROOT);
        String hyphenated = NON_ALPHANUMERIC.matcher(lowercase).replaceAll("-");
        String trimmed = EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
        // Nome sem nenhum caractere alfanumérico (ex.: só emojis/símbolos) é um caso extremo, mas
        // não pode gerar slug vazio — cai num valor fixo em vez de falhar a criação do catálogo.
        return trimmed.isEmpty() ? "catalogo" : trimmed;
    }
}
