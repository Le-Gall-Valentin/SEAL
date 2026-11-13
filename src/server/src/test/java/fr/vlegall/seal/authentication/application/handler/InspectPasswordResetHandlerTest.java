package fr.vlegall.seal.authentication.application.handler;

import static fr.vlegall.seal.authentication.application.handler.PasswordResetFixture.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import fr.vlegall.seal.authentication.domain.model.ResetView;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InspectPasswordResetHandlerTest {

    private final PasswordResetFixture fixture = new PasswordResetFixture();
    private final InspectPasswordResetHandler handler =
            new InspectPasswordResetHandler(fixture.tokens, fixture.accounts, fixture.clock);

    @Test
    @DisplayName("AUTH-70 : un lien valable donne l'identifiant, le courriel et l'expiration (ADR 0022)")
    void auth70LiveLinkGivesTheAccountAndItsExpiry() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());

        assertThat(handler.inspect(token.value()))
                .contains(new ResetView("valentin", "valentin@exemple.ca", NOW.plus(Duration.ofMinutes(30))));
    }

    @Test
    @DisplayName("AUTH-70 : un lien mal formé, inconnu, expiré ou utilisé ne donne rien")
    void auth70DeadLinkGivesNothing() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken used = fixture.issue(valentin.user());
        ResetToken live = fixture.issue(valentin.user());

        assertThat(handler.inspect("pas-un-jeton")).isEmpty();
        assertThat(handler.inspect(ResetToken.of(new byte[ResetToken.BYTES]).value()))
                .isEmpty();
        assertThat(handler.inspect(used.value())).as("invalidé par le suivant").isEmpty();
        fixture.clock.advance(Duration.ofMinutes(30));
        assertThat(handler.inspect(live.value())).as("expiré").isEmpty();
    }

    @Test
    @DisplayName("AUTH-70 : le lien d'un compte qui n'est plus actif ne donne rien")
    void auth70LinkOfAnInactiveAccountGivesNothing() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken token = fixture.issue(valentin.user());
        fixture.contacts.remove(valentin.user());

        assertThat(handler.inspect(token.value())).isEmpty();
    }
}
