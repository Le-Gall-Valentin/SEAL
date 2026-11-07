package fr.vlegall.seal.security.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogValuesTest {

    @Test
    @DisplayName("SEC-21 : un courriel est masqué en v***@exemple.ca")
    void sec21EmailIsMasked() {
        assertThat(LogValues.maskEmail("valentin@exemple.ca")).isEqualTo("v***@exemple.ca");
        assertThat(LogValues.maskEmail("v@exemple.ca")).isEqualTo("v***@exemple.ca");
    }

    @Test
    @DisplayName("SEC-21 : une valeur sans partie locale ne laisse rien paraître")
    void sec21MalformedEmailIsFullyMasked() {
        assertThat(LogValues.maskEmail("pas-un-courriel")).isEqualTo("***");
        assertThat(LogValues.maskEmail("@exemple.ca")).isEqualTo("***");
    }

    @Test
    @DisplayName("A05 : le domaine d'un courriel masqué est aussi assaini")
    void a05MaskedEmailDomainIsSanitised() {
        assertThat(LogValues.maskEmail("a@ex\nample.ca")).isEqualTo("a***@ex_ample.ca");
    }

    @Test
    @DisplayName("A05 : les caractères de contrôle et les fins de ligne Unicode sont neutralisés")
    void a05ControlCharactersAreNeutralised() {
        assertThat(LogValues.sanitize("bob\r\nINFO faux\u2028x")).isEqualTo("bob__INFO faux_x");
        assertThat(LogValues.sanitize("a\tb\u007fc\u0085d\u2029")).isEqualTo("a_b_c_d_");
    }

    @Test
    @DisplayName("SEC-21 : un identifiant saisi est masqué s'il ressemble à un courriel, assaini sinon")
    void identifierMasksEmailsAndSanitisesTheRest() {
        assertThat(LogValues.identifier("Bob@Exemple.ca")).isEqualTo("B***@Exemple.ca");
        assertThat(LogValues.identifier("bob\n")).isEqualTo("bob_");
    }
}
