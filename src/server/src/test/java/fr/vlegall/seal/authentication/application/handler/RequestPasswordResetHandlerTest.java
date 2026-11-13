package fr.vlegall.seal.authentication.application.handler;

import static fr.vlegall.seal.authentication.application.handler.PasswordResetFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestPasswordResetHandlerTest {

    private final PasswordResetFixture fixture = new PasswordResetFixture();
    private final RequestPasswordResetHandler handler = new RequestPasswordResetHandler(
            fixture.accounts, fixture.credentials, fixture.tokens, fixture.mail, fixture.random(), fixture.clock);

    @Test
    @DisplayName("AUTH-70 : un compte actif qui a un mot de passe reçoit un lien valable 30 minutes")
    void auth70ActiveAccountWithPasswordGetsALink() {
        AccountContact valentin = fixture.active("valentin", true);

        handler.request("valentin@exemple.ca");

        assertThat(fixture.mails).singleElement().satisfies(sent -> {
            assertThat(sent.email()).isEqualTo("valentin@exemple.ca");
            assertThat(sent.username()).isEqualTo("valentin");
            assertThat(fixture.tokens.findLive(sent.token().hash(), NOW)).hasValueSatisfying(live -> {
                assertThat(live.user()).isEqualTo(valentin.user());
                assertThat(live.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
            });
        });
        assertThat(fixture.calls)
                .as("le verrou du compte d'abord (ADR 0022, point 4)")
                .containsExactly("verrou", "émission", "courriel");
    }

    @Test
    @DisplayName("AUTH-70 : un courriel inconnu ou d'un compte sans mot de passe ne déclenche rien, sans erreur")
    void auth70UnknownOrPasswordlessAccountGetsNothing() {
        fixture.active("oauth", false);

        handler.request("inconnu@exemple.ca");
        handler.request("oauth@exemple.ca");

        assertThat(fixture.mails).isEmpty();
        assertThat(fixture.rows).isEmpty();
        assertThat(fixture.calls)
                .as("le verrou, refusé faute de mot de passe, est le seul effet : ni émission ni courriel")
                .containsExactly("verrou");
    }

    @Test
    @DisplayName("AUTH-70 : une nouvelle demande invalide le lien précédent ; seul le dernier sert (ADR 0022)")
    void auth70NewRequestInvalidatesThePreviousLink() {
        fixture.active("valentin", true);

        handler.request("valentin@exemple.ca");
        fixture.clock.advance(Duration.ofMinutes(1));
        handler.request("valentin@exemple.ca");

        ResetToken first = fixture.mails.get(0).token();
        ResetToken second = fixture.mails.get(1).token();
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(fixture.tokens.findLive(first.hash(), fixture.clock.instant()))
                .isEmpty();
        assertThat(fixture.tokens.findLive(second.hash(), fixture.clock.instant()))
                .isPresent();
    }
}
