package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-13, ADR 0026 (point 8) : le lanceur de développement tire un poivre jetable si aucun n'est donné. */
@DisplayName("AUTH-13 : poivre du mode développement")
class TestSealApplicationTest {

    @Test
    @DisplayName("AUTH-13, ADR 0026 (point 8) : le poivre jetable fait 32 octets et change à chaque lancement")
    void auth13RandomPepperIsFreshAndThirtyTwoBytes() {
        String first = TestSealApplication.randomPepper();
        String second = TestSealApplication.randomPepper();

        assertThat(Base64.getDecoder().decode(first)).hasSize(32);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("AUTH-13, ADR 0026 (point 8) : un poivre donné en propriété système ou en argument n'est pas remplacé")
    void auth13GivenPepperIsKept() {
        assumeThat(System.getenv(TestSealApplication.PEPPER_VARIABLE)).isNull();
        assertThat(TestSealApplication.pepperGiven(new String[0])).isFalse();
        assertThat(TestSealApplication.pepperGiven(new String[] {"--seal.passwords.pepper=donne"}))
                .isTrue();
        System.setProperty(TestSealApplication.PEPPER_VARIABLE, "donne");
        try {
            assertThat(TestSealApplication.pepperGiven(new String[0])).isTrue();
        } finally {
            System.clearProperty(TestSealApplication.PEPPER_VARIABLE);
        }
    }
}
