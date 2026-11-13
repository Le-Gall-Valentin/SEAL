package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnicodeTest {

    @Test
    @DisplayName("SEC-01 : NFC compose les caractères décomposés")
    void sec01NfcComposes() {
        assertThat(Unicode.nfc("Émile")).isEqualTo("Émile");
        assertThat(Unicode.isNfc("Émile")).isTrue();
        assertThat(Unicode.isNfc("Émile")).isFalse();
    }

    @Test
    @DisplayName("AUTH-11 : NFKC ramène les formes de compatibilité à leur forme simple")
    void auth11NfkcFoldsCompatibilityForms() {
        assertThat(Unicode.nfkc("ＡＢＣﬁ")).isEqualTo("ABCfi");
    }

    @Test
    @DisplayName("AUTH-11 : les minuscules suivent la correspondance par défaut d'Unicode, quelle que soit la langue")
    void auth11LowerCaseIsLocaleIndependent() {
        assertThat(Unicode.lowerCase("TITLE İ")).isEqualTo("title i̇");
    }

    @Test
    @DisplayName("AUTH-04 : le pliage décompose en NFKD, retire les diacritiques et passe en minuscules")
    void auth04FoldRemovesMarks() {
        assertThat(Unicode.foldWithoutMarks("Valéntin İﬁ")).isEqualTo("valentin ifi");
    }

    @Test
    @DisplayName("AUTH-11 : un texte bien formé n'a aucune demi-paire isolée ; une paire complète l'est")
    void auth11WellFormedMeansNoLoneSurrogate() {
        assertThat(Unicode.isWellFormed("phoque 🦭")).isTrue();
        assertThat(Unicode.isWellFormed("")).isTrue();
        assertThat(Unicode.isWellFormed("a" + Character.toString(0xD83E))).isFalse();
        assertThat(Unicode.isWellFormed(Character.toString(0xDE6D) + "a")).isFalse();
    }

    @Test
    @DisplayName("AUTH-20 : la saisie d'une connexion est normalisée en NFC puis passée en minuscules (ADR 0021)")
    void auth20LoginInputIsFolded() {
        assertThat(Unicode.foldLogin("Valentin.Le.Gall")).isEqualTo("valentin.le.gall");
        assertThat(Unicode.foldLogin("V@EXEMPLE.ca")).isEqualTo("v@exemple.ca");
        assertThat(Unicode.foldLogin("Vale\u0301ntin@exemple.ca")).isEqualTo("val\u00e9ntin@exemple.ca");
    }

    @Test
    @DisplayName(
            "AUTH-22 : la saisie normalisée reste en NFC après les minuscules : « T » + tréma donne « ẗ » (ADR 0021)")
    void auth22FoldedLoginStaysNfc() {
        String decomposed = "T" + Character.toString(0x0308) + "eo@exemple.ca";
        String precomposed = Character.toString(0x1E97) + "eo@exemple.ca";

        assertThat(Unicode.foldLogin(decomposed)).isEqualTo(Unicode.foldLogin(precomposed));
        assertThat(Unicode.isNfc(Unicode.foldLogin(decomposed))).isTrue();
    }
}
