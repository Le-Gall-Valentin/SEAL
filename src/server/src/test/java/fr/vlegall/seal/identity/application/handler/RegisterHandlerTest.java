package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.RegisterCommand;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegisterHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final RegisterHandler handler = identity.registerHandler();

    private static RegisterCommand command(String email, String username) {
        return new RegisterCommand(
                EmailAddress.parse(email).orElseThrow(),
                username,
                fr.vlegall.seal.identity.domain.model.DisplayName.parse("Valentin")
                        .orElseThrow(),
                "fjord-tuque-raquette");
    }

    @Test
    @DisplayName("AUTH-10 : l'inscription crée un compte en attente, son mot de passe, son code et le courriel du code")
    void auth10RegistrationCreatesAPendingAccount() {
        UserId id = handler.register(command("Valentin.Le-Gall+uqac@exemple.ca", "valentin.le.gall"));

        Account account = identity.accounts.get(id);
        assertThat(account.status()).isEqualTo(AccountStatus.PENDING_EMAIL);
        assertThat(account.username().value()).isEqualTo("valentin.le.gall");
        assertThat(identity.personalWords).containsExactly(List.of("valentin.le.gall", "Valentin.Le-Gall"));
        assertThat(identity.storedPasswords).containsExactly(Map.entry(id, "hash:fjord-tuque-raquette"));
        assertThat(identity.events).containsExactly("registered:" + id.value() + ":Valentin.Le-Gall+uqac@exemple.ca");
        assertThat(identity.codes).singleElement().satisfies(code -> {
            assertThat(code.user()).isEqualTo(id);
            assertThat(code.hash())
                    .isEqualTo(VerificationCode.parse("123456").orElseThrow().hash());
            assertThat(code.expiresAt()).isEqualTo(InMemoryIdentity.NOW.plus(CodePolicy.VALIDITY));
        });
        assertThat(identity.mails).containsExactly("code:Valentin.Le-Gall+uqac@exemple.ca:valentin.le.gall:123456");
    }

    @Test
    @DisplayName(
            "AUTH-15 : si un compte actif possède l'adresse, le compte est créé mais son titulaire reçoit l'avertissement")
    void auth15ExistingActiveAddressGetsAWarningInsteadOfTheCode() {
        identity.add("titulaire", "Alice@Exemple.ca", AccountStatus.ACTIVE);

        UserId id = handler.register(command("alice@exemple.ca", "alice.bis"));

        assertThat(identity.accounts.get(id).status()).isEqualTo(AccountStatus.PENDING_EMAIL);
        assertThat(identity.codes)
                .as("le code existe, comme pour une adresse libre")
                .hasSize(1);
        assertThat(identity.mails).containsExactly("warning:alice@exemple.ca");
    }

    @Test
    @DisplayName("AUTH-15 : un compte en attente ne réserve pas l'adresse ; le nouveau compte reçoit son code")
    void auth15PendingAccountDoesNotReserveTheAddress() {
        identity.add("squatteur", "alice@exemple.ca", AccountStatus.PENDING_EMAIL);

        handler.register(command("alice@exemple.ca", "alice"));

        assertThat(identity.mails).containsExactly("code:alice@exemple.ca:alice:123456");
    }

    @Test
    @DisplayName("AUTH-02 : un nom réservé est refusé avec USERNAME_RESERVED, sans rien créer")
    void auth02ReservedNameIsRefused() {
        assertThatThrownBy(() -> handler.register(command("a@exemple.ca", "admin")))
                .isInstanceOf(IdentityException.UsernameReserved.class);
        assertThat(identity.accounts).isEmpty();
        assertThat(identity.mails).isEmpty();
    }

    @Test
    @DisplayName("AUTH-01 : un identifiant mal formé est refusé avec USERNAME_INVALID")
    void auth01MalformedUsernameIsRefused() {
        assertThatThrownBy(() -> handler.register(command("a@exemple.ca", "Valentin")))
                .isInstanceOf(IdentityException.UsernameInvalid.class);
        assertThat(identity.accounts).isEmpty();
    }

    @Test
    @DisplayName("AUTH-02 : un identifiant pris est refusé avec USERNAME_TAKEN")
    void auth02TakenUsernameIsRefused() {
        identity.add("valentin", "autre@exemple.ca", AccountStatus.PENDING_EMAIL);

        assertThatThrownBy(() -> handler.register(command("a@exemple.ca", "valentin")))
                .isInstanceOf(IdentityException.UsernameTaken.class);
        assertThat(identity.personalWords)
                .as("aucun hachage pour un identifiant pris")
                .isEmpty();
        assertThat(identity.mails).isEmpty();
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe refusé répond PASSWORD_TOO_WEAK, sans journal, sans code ni courriel")
    void auth11WeakPasswordIsRefused() {
        identity.passwordAccepted = false;

        assertThatThrownBy(() -> handler.register(command("a@exemple.ca", "valentin")))
                .isInstanceOf(IdentityException.PasswordTooWeak.class);
        assertThat(identity.accounts).as("refusé avant la création du compte").isEmpty();
        assertThat(identity.events).isEmpty();
        assertThat(identity.codes).isEmpty();
        assertThat(identity.mails).isEmpty();
    }

    @Test
    @DisplayName("SEC-01 : le nom affiché enregistré est celui de la commande")
    void sec01DisplayNameIsKept() {
        UserId id = handler.register(command("a@exemple.ca", "valentin"));

        assertThat(identity.accounts.get(id).displayName().value()).isEqualTo("Valentin");
    }
}
