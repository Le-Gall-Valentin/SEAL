package fr.vlegall.seal.authentication.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordResetDtoToStringTest {

    private static final String EMAIL = "valentin@exemple.ca";
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE";

    @Test
    @DisplayName("SEC-21 : aucun DTO de la réinitialisation ne montre le courriel, le jeton ou le mot de passe")
    void sec21DtosHideTheirSecrets() {
        List<Object> values = List.of(
                new PasswordResetRequest(EMAIL),
                new PasswordResetView("valentin", EMAIL, Instant.parse("2026-11-13T12:30:00Z")),
                new PasswordResetInspectRequest(TOKEN),
                new PasswordResetConfirmRequest(TOKEN, "nouvelle-phrase-de-passe"));

        assertThat(values)
                .allSatisfy(value -> assertThat(value.toString())
                        .doesNotContain(EMAIL)
                        .doesNotContain(TOKEN)
                        .doesNotContain("nouvelle-phrase-de-passe"));
    }
}
