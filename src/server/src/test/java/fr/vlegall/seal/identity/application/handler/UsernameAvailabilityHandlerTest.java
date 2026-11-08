package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.UsernameAvailability;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsernameAvailabilityHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final UsernameAvailabilityHandler handler =
            new UsernameAvailabilityHandler(identity.users(), identity.random());

    @Test
    @DisplayName("AUTH-05 : un identifiant libre est disponible, avec trois autres suggestions")
    void auth05FreeUsernameIsAvailable() {
        UsernameAvailability result = handler.check("valentin");

        assertThat(result.available()).isTrue();
        assertThat(result.suggestions()).containsExactly("valentin2", "valentin3", "valentin4");
    }

    @Test
    @DisplayName("AUTH-05 : un identifiant pris n'est pas disponible ; les suggestions sautent ceux qui sont pris")
    void auth05TakenUsernameSuggestsFreeOnes() {
        identity.add("valentin", "a@exemple.ca", AccountStatus.ACTIVE);
        identity.add("valentin2", "b@exemple.ca", AccountStatus.PENDING_EMAIL);
        identity.add("valentin4", "c@exemple.ca", AccountStatus.ACTIVE);

        UsernameAvailability result = handler.check("valentin");

        assertThat(result.available()).isFalse();
        assertThat(result.suggestions()).containsExactly("valentin3", "valentin5", "valentin6");
    }

    @Test
    @DisplayName("AUTH-02 : un nom réservé n'est pas disponible")
    void auth02ReservedNameIsNotAvailable() {
        UsernameAvailability result = handler.check("admin");

        assertThat(result.available()).isFalse();
        assertThat(result.suggestions()).containsExactly("admin2", "admin3", "admin4");
    }

    @Test
    @DisplayName("AUTH-04 : un identifiant mal formé n'est pas disponible ; sa forme normalisée est proposée d'abord")
    void auth04MalformedUsernameSuggestsItsNormalisedForm() {
        UsernameAvailability result = handler.check("Valentin Dupont");

        assertThat(result.available()).isFalse();
        assertThat(result.suggestions()).containsExactly("valentin.dupont", "valentin.dupont2", "valentin.dupont3");
    }

    @Test
    @DisplayName("AUTH-04 : quand les suffixes 2 à 99 sont pris, quatre chiffres aléatoires sont essayés")
    void auth04RandomDigitsWhenSuffixesAreExhausted() {
        identity.add("user", "u@exemple.ca", AccountStatus.ACTIVE);
        for (int suffix = 2; suffix <= 99; suffix++) {
            identity.add("user" + suffix, "u" + suffix + "@exemple.ca", AccountStatus.ACTIVE);
        }
        identity.digits.clear();
        identity.digits.addAll(List.of("0042", "0042", "0043", "0044"));

        UsernameAvailability result = handler.check("user");

        assertThat(result.suggestions()).containsExactly("user0042", "user0043", "user0044");
    }
}
