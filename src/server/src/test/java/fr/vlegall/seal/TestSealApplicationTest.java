package fr.vlegall.seal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ADR 0026, point 8 : le lanceur de développement tire un poivre jetable si l'environnement n'en donne pas. */
@DisplayName("ADR 0026 : poivre du mode développement")
class TestSealApplicationTest {

    @Test
    @DisplayName("ADR 0026, point 8 : sans poivre, un poivre aléatoire de 32 octets, différent à chaque lancement")
    void adr0026MissingPepperIsRandom() {
        String first = TestSealApplication.developmentPepper(null);
        String second = TestSealApplication.developmentPepper("  ");

        assertThat(Base64.getDecoder().decode(first)).hasSize(32);
        assertThat(Base64.getDecoder().decode(second)).hasSize(32);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("ADR 0026, point 8 : un poivre déjà fourni par l'environnement est gardé tel quel")
    void adr0026GivenPepperIsKept() {
        String given = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(TestSealApplication.developmentPepper(given)).isEqualTo(given);
    }
}
