package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Le record du domaine porte le nom de l'annotation de JUnit, écrite ici avec son nom complet. */
class DisplayNameTest {

    @Test
    @org.junit.jupiter.api.DisplayName("AUTH-10 : un nom affiché ordinaire est accepté")
    void auth10AcceptsAnOrdinaryName() {
        assertThat(DisplayName.parse("Valentin Dupont")).map(DisplayName::value).contains("Valentin Dupont");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-01 : le nom affiché est normalisé en NFC")
    void sec01NameIsNfcNormalised() {
        assertThat(DisplayName.parse("E\u0301mile")).map(DisplayName::value).contains("Émile");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("AUTH-10 : 50 points de code sont acceptés, même 50 émojis (100 unités UTF-16)")
    void auth10FiftyCodePointsAreAccepted() {
        assertThat(DisplayName.parse("a".repeat(50))).isPresent();
        assertThat(DisplayName.parse("🦭".repeat(50))).isPresent();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("AUTH-10 : 51 points de code, ou un nom vide ou blanc, sont refusés")
    void auth10TooLongOrEmptyNamesAreRefused() {
        assertThat(DisplayName.parse("a".repeat(51))).isEmpty();
        assertThat(DisplayName.parse("")).isEmpty();
        assertThat(DisplayName.parse("   ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Bob\u202Eevil",
                "Bob\u202A",
                "Bob\u200E",
                "Bob\u200F",
                "Bob\u061C",
                "Bob\u2066",
                "Bob\u2069",
                "Bob\u0007",
                "Bob\nAlice",
                "Bob\u2028Alice",
                "Bob\u2029Alice",
                "Bob\u0085"
            })
    @org.junit.jupiter.api.DisplayName(
            "SEC-01 : un caractère de contrôle, de direction ou un séparateur de ligne est refusé")
    void sec01ControlAndDirectionCharactersAreRefused(String raw) {
        assertThat(DisplayName.parse(raw)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bob\uD800", "\uDC00Bob", "Bob\u0378"})
    @org.junit.jupiter.api.DisplayName(
            "SEC-01 : une demi-paire de substitution isolée ou un point de code non attribué est refusé")
    void sec01MalformedTextIsRefused(String raw) {
        assertThat(DisplayName.parse(raw)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u200B", "\u200B\u200D", "\u3164", "\u115F\u1160", "\uFFA0", "\u2800", "\u0301"})
    @org.junit.jupiter.api.DisplayName("AUTH-10 : un nom fait seulement de caractères invisibles est refusé")
    void auth10InvisibleNamesAreRefused(String raw) {
        assertThat(DisplayName.parse(raw)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
                "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645",
                "\uD83E\uDDAD",
                "\u00B7"
            })
    @org.junit.jupiter.api.DisplayName(
            "AUTH-10 : les émojis composés (ZWJ) et les écritures qui emploient ZWNJ restent permis")
    void auth10JoinersInLegitimateNamesArePermitted(String raw) {
        assertThat(DisplayName.parse(raw)).isPresent();
    }
}
