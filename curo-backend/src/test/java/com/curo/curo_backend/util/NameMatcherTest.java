package com.curo.curo_backend.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameMatcherTest {

    @Test
    void ignoresCaseSpacingPunctuationAndTitles() {
        assertThat(NameMatcher.matches("Anjali Varadarajan", "anjali   varadarajan")).isTrue();
        assertThat(NameMatcher.matches("Dr. Anjali Varadarajan", "Anjali Varadarajan")).isTrue();
        assertThat(NameMatcher.matches("DR ANJALI VARADARAJAN", "Anjali Varadarajan")).isTrue();
        assertThat(NameMatcher.matches("Dr Prof Anjali Varadarajan", "Anjali Varadarajan")).isTrue();
        assertThat(NameMatcher.matches("Sri Velan Medicals.", "sri velan medicals")).isTrue();
        assertThat(NameMatcher.matches("Aditya Kulkarni-Deshpande", "Aditya Kulkarni Deshpande")).isTrue();
        assertThat(NameMatcher.matches("Jose", "José")).isTrue();
    }

    @Test
    void differentNamesDoNotMatch() {
        assertThat(NameMatcher.matches("Anjali Varadarajan", "Anjali Varadarajah")).isFalse();
        assertThat(NameMatcher.matches("Anjali", "Anjali Varadarajan")).isFalse();
        assertThat(NameMatcher.matches("Varadarajan Anjali", "Anjali Varadarajan")).isFalse();
    }

    @Test
    void blankOrNullNeverMatches() {
        assertThat(NameMatcher.matches("", "")).isFalse();
        assertThat(NameMatcher.matches(null, null)).isFalse();
        assertThat(NameMatcher.matches("Dr.", "Dr.")).isTrue(); // a lone title is kept, not erased
        assertThat(NameMatcher.matches("  ", "Anjali")).isFalse();
    }

    @Test
    void aTitleIsOnlyStrippedAtTheStart() {
        assertThat(NameMatcher.normalize("Ms Dr")).isEqualTo("dr");
        assertThat(NameMatcher.normalize("Anjali Dr")).isEqualTo("anjali dr");
    }
}
