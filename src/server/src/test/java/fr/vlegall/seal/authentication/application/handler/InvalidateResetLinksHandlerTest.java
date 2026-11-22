package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvalidateResetLinksHandlerTest {

    private final PasswordFixture fixture = new PasswordFixture();
    private final InvalidateResetLinksHandler handler =
            new InvalidateResetLinksHandler(fixture.credentials, fixture.tokens, fixture.clock);

    @Test
    @DisplayName("ACC-02, ADR 0034 : les liens vivants meurent, après le verrou du compte qui sérialise leur émission")
    void acc02InvalidatesLiveLinksUnderTheAccountLock() {
        AccountContact valentin = fixture.active("valentin", true);
        ResetToken link = fixture.issue(valentin.user());

        handler.invalidate(valentin.user());

        assertThat(fixture.calls).containsExactly("verrou");
        assertThat(fixture.tokens.findLive(link.hash(), fixture.clock.instant()))
                .isEmpty();
    }
}
