package fr.vlegall.seal.authentication.application.handler;

import static fr.vlegall.seal.authentication.application.handler.PasswordFixture.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.common.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReauthenticateHandlerTest {

    private final PasswordFixture fixture = new PasswordFixture();
    private final ReauthenticateHandler handler = new ReauthenticateHandler(
            new CurrentPasswordCheck(fixture.accounts, fixture.credentials, fixture.hasher, fixture.loginJournal));

    private AccountContact withPassword() {
        AccountContact contact = fixture.active("valentin", false);
        fixture.credentials.save(contact.user(), "hash(" + PASSWORD + ")");
        return contact;
    }

    @Test
    @DisplayName("AUTH-55 : le bon mot de passe ré-authentifie, sans rien journaliser")
    void auth55RightPasswordReauthenticates() {
        AccountContact contact = withPassword();

        assertThat(handler.reauthenticate(contact.user(), PASSWORD)).isTrue();
        assertThat(fixture.calls).containsExactly("vérification");
    }

    @Test
    @DisplayName("AUTH-55, ADR 0034 : un mauvais mot de passe est refusé et journalisé comme un échec de connexion")
    void auth55WrongPasswordIsRefusedAndJournaled() {
        AccountContact contact = withPassword();

        assertThat(handler.reauthenticate(contact.user(), "pas-le-bon-mot-de-passe"))
                .isFalse();
        assertThat(fixture.calls)
                .containsExactly("vérification", "échec:" + contact.user().value());
    }

    @Test
    @DisplayName("AUTH-55, ADR 0034 : un compte sans mot de passe est refusé après un leurre, au même coût")
    void auth55AccountWithoutPasswordVerifiesTheDecoy() {
        AccountContact oauth = fixture.active("oauth", false);

        assertThat(handler.reauthenticate(oauth.user(), PASSWORD)).isFalse();
        assertThat(fixture.calls)
                .containsExactly("leurre", "échec:" + oauth.user().value());
    }

    @Test
    @DisplayName("SEC-03 : un compte qui n'est plus actif est refusé après un leurre (échec fermé)")
    void sec03InactiveAccountIsRefused() {
        UserId gone = new UserId(UUID.randomUUID());
        fixture.credentials.save(gone, "hash(" + PASSWORD + ")");

        assertThat(handler.reauthenticate(gone, PASSWORD)).isFalse();
        assertThat(fixture.calls).containsExactly("leurre", "échec:" + gone.value());
    }
}
