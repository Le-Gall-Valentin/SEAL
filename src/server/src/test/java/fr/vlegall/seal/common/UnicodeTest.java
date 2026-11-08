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
}
