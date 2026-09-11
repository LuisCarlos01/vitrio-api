package dev.vitrio.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void lowercasesAndHyphenatesSpaces() {
        assertThat(SlugGenerator.slugify("Catalogo Boticario")).isEqualTo("catalogo-boticario");
    }

    @Test
    void stripsAccents() {
        assertThat(SlugGenerator.slugify("Catálogo - Boticário")).isEqualTo("catalogo-boticario");
    }

    @Test
    void collapsesRepeatedNonAlphanumericIntoSingleHyphen() {
        assertThat(SlugGenerator.slugify("Semi  //  Joias!!!")).isEqualTo("semi-joias");
    }

    @Test
    void trimsHyphensFromEdges() {
        assertThat(SlugGenerator.slugify("--Catalogo--")).isEqualTo("catalogo");
    }

    @Test
    void fallsBackToFixedValueWhenNameHasNoAlphanumericChars() {
        assertThat(SlugGenerator.slugify("!!!")).isEqualTo("catalogo");
    }
}
