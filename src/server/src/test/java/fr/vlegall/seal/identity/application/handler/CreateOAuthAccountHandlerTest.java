package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-32 : compte d'une inscription OAuth (ADR 0032). */
@DisplayName("AUTH-32 : compte d'une inscription OAuth")
class CreateOAuthAccountHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final CreateOAuthAccountHandler handler = new CreateOAuthAccountHandler(
            identity.users(), identity.journal(), identity.codeDelivery(), identity.clock);

    @Test
    @DisplayName("AUTH-32 : courriel vérifié, compte actif d'emblée, journalisé, sans code envoyé")
    void auth32VerifiedEmailMakesAnActiveAccount() {
        UserId id = handler.create("valentin", "Valentin", "valentin@gmail.com", true);

        Account account = identity.accounts.get(id);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.displayName().value()).isEqualTo("Valentin");
        assertThat(identity.events).containsExactly("registered:" + id.value() + ":valentin@gmail.com");
        assertThat(identity.codes).isEmpty();
        assertThat(identity.mails).isEmpty();
    }

    @Test
    @DisplayName("ADR 0032 : courriel non vérifié, compte en attente et code SEAL envoyé, comme à l'inscription")
    void adr0032UnverifiedEmailMakesAPendingAccount() {
        UserId id = handler.create("octo", "Octo", "octo@exemple.ca", false);

        assertThat(identity.accounts.get(id).status()).isEqualTo(AccountStatus.PENDING_EMAIL);
        assertThat(identity.codes).hasSize(1);
        assertThat(identity.mails).containsExactly("code:octo@exemple.ca:octo:123456");
    }

    @Test
    @DisplayName("AUTH-01 à AUTH-03 : identifiant invalide, réservé ou pris, dans cet ordre, sans rien créer")
    void auth01ToAuth03UsernameRules() {
        identity.add("pris", "pris@exemple.ca", AccountStatus.DELETED);

        assertThatThrownBy(() -> handler.create("A!", "Nom", "a@exemple.ca", true))
                .isInstanceOf(IdentityException.UsernameInvalid.class);
        assertThatThrownBy(() -> handler.create("admin", "Nom", "a@exemple.ca", true))
                .isInstanceOf(IdentityException.UsernameReserved.class);
        assertThatThrownBy(() -> handler.create("pris", "Nom", "a@exemple.ca", true))
                .as("AUTH-03 : jamais réattribué, même après suppression")
                .isInstanceOf(IdentityException.UsernameTaken.class);
        assertThat(identity.accounts).hasSize(1);
    }

    @Test
    @DisplayName("ACC-03 : un nom affiché refusé donne VALIDATION_FAILED sur displayName")
    void acc03InvalidDisplayName() {
        assertThatThrownBy(() -> handler.create("nom", "Vale‮ntin", "n@exemple.ca", true))
                .isInstanceOfSatisfying(
                        SealException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(identity.accounts).isEmpty();
    }

    @Test
    @DisplayName(
            "AUTH-33 : courriel vérifié déjà pris par un compte actif, OAUTH_EMAIL_IN_USE ; un compte en attente ne compte pas")
    void auth33VerifiedEmailOfAnActiveAccount() {
        identity.add("actif", "pris@exemple.ca", AccountStatus.ACTIVE);
        identity.add("attente", "libre@exemple.ca", AccountStatus.PENDING_EMAIL);

        assertThatThrownBy(() -> handler.create("nouveau", "Nom", "pris@exemple.ca", true))
                .isInstanceOfSatisfying(
                        IdentityException.EmailInUse.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.OAUTH_EMAIL_IN_USE));
        assertThat(identity.accounts
                        .get(handler.create("autre", "Nom", "libre@exemple.ca", true))
                        .status())
                .isEqualTo(AccountStatus.ACTIVE);
    }
}
