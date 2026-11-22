package fr.vlegall.seal.authentication.application.handler;

import static fr.vlegall.seal.authentication.application.handler.PasswordFixture.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.PasswordChangeOutcome;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChangePasswordHandlerTest {

    private static final String NEW_PASSWORD = "raquette-sous-la-neige";
    private static final String SESSION = "session-courante";

    private final PasswordFixture fixture = new PasswordFixture();
    private final ChangePasswordHandler handler = new ChangePasswordHandler(
            new CurrentPasswordCheck(fixture.accounts, fixture.credentials, fixture.hasher, fixture.loginJournal),
            fixture.preparation(),
            new PasswordChangeWriter(
                    fixture.credentials, fixture.tokens, fixture.changeJournal, fixture.changeMail, fixture.clock),
            fixture.sessions);

    private AccountContact withPassword() {
        AccountContact contact = fixture.active("valentin", false);
        fixture.credentials.save(contact.user(), "hash(" + PASSWORD + ")");
        return contact;
    }

    @Test
    @DisplayName(
            "ACC-01 : le bon mot de passe actuel remplace le haché, avec une nouvelle version, et déconnecte les autres sessions")
    void acc01ChangesThePasswordAndSignsOutTheOthers() {
        AccountContact contact = withPassword();
        ResetToken link = fixture.issue(contact.user());

        PasswordChangeOutcome outcome = handler.change(contact.user(), PASSWORD, NEW_PASSWORD, true, SESSION);

        assertThat(outcome).isEqualTo(new PasswordChangeOutcome.Changed(2L));
        assertThat(fixture.credentials.hashes()).containsEntry(contact.user(), "hash(" + NEW_PASSWORD + ")");
        assertThat(fixture.calls)
                .containsExactly(
                        "vérification",
                        "hachage",
                        "journal-changement:" + contact.user().value(),
                        "courriel-changement:" + contact.email(),
                        "révocation-autres:" + contact.user().value() + ":" + SESSION);
        assertThat(fixture.tokens.findLive(link.hash(), fixture.clock.instant()))
                .as("un lien de réinitialisation émis avant ne sert plus (ADR 0034)")
                .isEmpty();
    }

    @Test
    @DisplayName("ACC-01, ADR 0034 (point 1) : option décochée, le haché change sans nouvelle version ni révocation")
    void acc01UncheckedOptionKeepsTheOtherSessions() {
        AccountContact contact = withPassword();

        PasswordChangeOutcome outcome = handler.change(contact.user(), PASSWORD, NEW_PASSWORD, false, SESSION);

        assertThat(outcome).isEqualTo(new PasswordChangeOutcome.Changed(1L));
        assertThat(fixture.credentials.hashes()).containsEntry(contact.user(), "hash(" + NEW_PASSWORD + ")");
        assertThat(fixture.calls).noneMatch(call -> call.startsWith("révocation"));
        assertThat(fixture.calls).contains("courriel-changement:" + contact.email());
    }

    @Test
    @DisplayName("ACC-01 : un mauvais mot de passe actuel est refusé et journalisé ; rien n'est haché ni écrit")
    void acc01WrongCurrentPasswordChangesNothing() {
        AccountContact contact = withPassword();

        PasswordChangeOutcome outcome =
                handler.change(contact.user(), "pas-le-bon-mot-de-passe", NEW_PASSWORD, true, SESSION);

        assertThat(outcome).isEqualTo(new PasswordChangeOutcome.InvalidCurrentPassword());
        assertThat(fixture.calls)
                .containsExactly("vérification", "échec:" + contact.user().value());
        assertThat(fixture.credentials.hashes()).containsEntry(contact.user(), "hash(" + PASSWORD + ")");
    }

    @Test
    @DisplayName("ACC-01, AUTH-11 : un nouveau mot de passe hors de la politique est refusé ; rien n'est écrit")
    void acc01WeakNewPasswordChangesNothing() {
        AccountContact contact = withPassword();

        PasswordChangeOutcome outcome =
                handler.change(contact.user(), PASSWORD, "correcthorsebatterystaple", true, SESSION);

        assertThat(outcome).isEqualTo(new PasswordChangeOutcome.WeakPassword());
        assertThat(fixture.calls).containsExactly("vérification");
        assertThat(fixture.credentials.hashes()).containsEntry(contact.user(), "hash(" + PASSWORD + ")");
    }

    @Test
    @DisplayName("ACC-01, ADR 0034 : un compte sans mot de passe ne peut pas en définir un ; leurre, même coût")
    void acc01AccountWithoutPasswordIsRefused() {
        AccountContact oauth = fixture.active("oauth", false);

        PasswordChangeOutcome outcome = handler.change(oauth.user(), PASSWORD, NEW_PASSWORD, true, SESSION);

        assertThat(outcome).isEqualTo(new PasswordChangeOutcome.InvalidCurrentPassword());
        assertThat(fixture.calls)
                .containsExactly("leurre", "échec:" + oauth.user().value());
        assertThat(fixture.credentials.hashes()).doesNotContainKey(oauth.user());
    }

    @Test
    @DisplayName("ACC-01, ADR 0023 : un mot de passe remplacé pendant le hachage n'est pas écrasé")
    void acc01ConcurrentReplacementWins() {
        AccountContact contact = withPassword();
        fixture.duringHash = () -> fixture.credentials.changeHash(contact.user(), "hash(reinitialise-entre-temps)");

        PasswordChangeOutcome outcome = handler.change(contact.user(), PASSWORD, NEW_PASSWORD, true, SESSION);

        assertThat(outcome).isEqualTo(new PasswordChangeOutcome.InvalidCurrentPassword());
        assertThat(fixture.credentials.hashes()).containsEntry(contact.user(), "hash(reinitialise-entre-temps)");
        assertThat(fixture.calls).containsExactly("vérification", "hachage");
    }
}
