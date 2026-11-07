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
    @DisplayName("SEC-21 : un identifiant saisi est gardé s'il a la forme d'un identifiant (AUTH-01), masqué sinon")
    void sec21IdentifierKeepsOnlyWhatCouldBeAUsername() {
        assertThat(LogValues.identifier("Bob@Exemple.ca")).isEqualTo("B***@Exemple.ca");
        assertThat(LogValues.identifier("valentin.le.gall")).isEqualTo("valentin.le.gall");
    }

    @Test
    @DisplayName("SEC-21 : un mot de passe tapé dans le champ identifiant n'est jamais journalisé")
    void sec21PasswordTypedAsIdentifierIsMasked() {
        assertThat(LogValues.identifier("Correct Horse Battery Staple")).isEqualTo("***");
        assertThat(LogValues.identifier("bob\n")).isEqualTo("***");
    }

    @Test
    @DisplayName("SEC-21 : un mot de passe contenant @ n'est pas pris pour un courriel et n'est pas journalisé")
    void sec21PasswordWithAtSignIsMasked() {
        assertThat(LogValues.identifier("Tr0ub4dor@Horse-Batt")).isEqualTo("***");
        assertThat(LogValues.identifier("correct-horse@battery-staple")).isEqualTo("***");
        assertThat(LogValues.identifier("MonP@ssphraseTresLongue2026")).isEqualTo("***");
    }

    @Test
    @DisplayName("SEC-21 : seule une valeur de forme stricte est traitée comme un courriel")
    void sec21OnlyAStrictEmailIsMaskedAsAnEmail() {
        assertThat(LogValues.email("valentin@exemple.ca")).isEqualTo("v***@exemple.ca");
        assertThat(LogValues.email("a@b@exemple.ca")).isEqualTo("***");
        assertThat(LogValues.email("val entin@exemple.ca")).isEqualTo("***");
        assertThat(LogValues.email("valentin@exemple")).isEqualTo("***");
        assertThat(LogValues.email("valentin@exemple..ca")).isEqualTo("***");
        assertThat(LogValues.email("v@" + "a".repeat(250) + ".ca")).isEqualTo("***");
        assertThat(LogValues.identifier("Bob@Exemple.ca")).isEqualTo("B***@Exemple.ca");
    }

    @Test
    @DisplayName("A05 : les caractères de direction Unicode sont neutralisés")
    void a05DirectionalCharactersAreNeutralised() {
        assertThat(LogValues.sanitize("a\u202Eb\u2066c\u202Ad\u2069")).isEqualTo("a_b_c_d_");
    }
}
