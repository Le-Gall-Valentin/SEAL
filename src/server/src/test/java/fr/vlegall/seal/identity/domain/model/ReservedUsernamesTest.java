package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReservedUsernamesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "admin",
                "root",
                "system",
                "seal",
                "support",
                "security",
                "api",
                "null",
                "me",
                "uqac",
                "administrator",
                "moderator",
                "postmaster",
                "webmaster",
                "hostmaster",
                "abuse",
                "noreply",
                "help",
                "info",
                "contact",
                "anonymous",
                "deleted",
                "test"
            })
    @DisplayName("AUTH-02 : les 23 noms réservés de l'ADR 0007 sont refusés")
    void auth02ReservedNamesAreRefused(String name) {
        // « me » n'a que 2 caractères : AUTH-01 le refuse déjà, mais la liste le garde, comme le CDC.
        assertThat(ReservedUsernames.contains(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin2", "administrators", "valentin", "seal.team", "rooted"})
    @DisplayName("AUTH-02 : un nom proche d'un nom réservé reste permis")
    void auth02NearbyNamesArePermitted(String name) {
        assertThat(ReservedUsernames.contains(name)).isFalse();
    }

    @Test
    @DisplayName("AUTH-02 : la liste compte exactement 23 noms")
    void auth02ListHasTwentyThreeNames() {
        assertThat(ReservedUsernames.names()).hasSize(23);
    }
}
