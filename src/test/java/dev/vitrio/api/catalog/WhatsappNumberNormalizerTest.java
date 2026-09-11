package dev.vitrio.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WhatsappNumberNormalizerTest {

    @Test
    void prependsCountryCodeWhenNumberHasOnlyDddAndLocalNumber() {
        assertThat(WhatsappNumberNormalizer.normalize("(11) 91234-5678")).isEqualTo("5511912345678");
    }

    @Test
    void keepsCountryCodeWhenAlreadyPresent() {
        assertThat(WhatsappNumberNormalizer.normalize("+55 11 91234-5678")).isEqualTo("5511912345678");
    }

    @Test
    void acceptsEightDigitLocalNumberWithoutTheNinth() {
        assertThat(WhatsappNumberNormalizer.normalize("(11) 1234-5678")).isEqualTo("551112345678");
    }

    @Test
    void doesNotConfuseDdd55WithAnAlreadyPresentCountryCode() {
        // DDD 55 (Santa Maria/RS) sem +55 tem 10-11 dígitos, então precisa ganhar o prefixo
        // do país igual a qualquer outro DDD — é exatamente o caso que a normalização por
        // tamanho (em vez de checar o prefixo) evita confundir.
        assertThat(WhatsappNumberNormalizer.normalize("55 91234-5678")).isEqualTo("5555912345678");
    }

    @Test
    void rejectsNumberThatIsTooShortAfterStrippingSymbols() {
        assertThatThrownBy(() -> WhatsappNumberNormalizer.normalize("123"))
                .isInstanceOf(InvalidWhatsappNumberException.class);
    }

    @Test
    void rejectsNumberThatIsTooLongAfterStrippingSymbols() {
        assertThatThrownBy(() -> WhatsappNumberNormalizer.normalize("+1 555 123 4567 8900"))
                .isInstanceOf(InvalidWhatsappNumberException.class);
    }
}
