package fr.vlegall.seal.authentication.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResetValuesToStringTest {

    private static final String EMAIL = "valentin@exemple.ca";

    @Test
    @DisplayName(
            "SEC-21 : aucune valeur du domaine de la réinitialisation ne montre le courriel, les mots personnels ou le haché")
    void sec21ResetValuesHideTheEmail() {
        Instant expiresAt = Instant.parse("2026-11-13T12:30:00Z");
        List<Object> values = List.of(
                new AccountContact(new UserId(UUID.randomUUID()), "valentin", EMAIL, List.of("valentin", "fjordtuque")),
                new ResetView("valentin", EMAIL, expiresAt),
                new StoredCredential("{argon2}$argon2id$secret", 3));

        assertThat(values)
                .allSatisfy(value -> assertThat(value.toString())
                        .doesNotContain(EMAIL)
                        .doesNotContain("fjordtuque")
                        .doesNotContain("secret"));
    }
}
