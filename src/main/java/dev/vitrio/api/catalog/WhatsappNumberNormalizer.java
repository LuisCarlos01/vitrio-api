package dev.vitrio.api.catalog;

import java.util.regex.Pattern;

/**
 * Normaliza um número em formato brasileiro comum (com/sem {@code +55}, com símbolos) pro
 * formato internacional só-dígitos (spec 002, US2).
 */
final class WhatsappNumberNormalizer {

    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");
    private static final Pattern NORMALIZED = Pattern.compile("^55[0-9]{10,11}$");

    private WhatsappNumberNormalizer() {}

    static String normalize(String rawNumber) {
        String digits = NON_DIGITS.matcher(rawNumber).replaceAll("");
        // DDD 55 (Santa Maria/RS) existe de verdade, então não dá pra decidir só olhando se já
        // começa com "55" — usa o tamanho: só DDD+número (10-11 dígitos) nunca tem como ser
        // confundido com código do país (12-13 dígitos), então prefixar aqui é seguro.
        String candidate = (digits.length() == 10 || digits.length() == 11) ? "55" + digits : digits;
        if (!NORMALIZED.matcher(candidate).matches()) {
            throw new InvalidWhatsappNumberException();
        }
        return candidate;
    }
}
