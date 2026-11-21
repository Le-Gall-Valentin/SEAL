package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestEmailChangeHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final RequestEmailChangeHandler handler = new RequestEmailChangeHandler(
            identity.users(),
            identity.emailCodes(),
            identity.mail(),
            identity.random(),
            identity.clock,
            identity.sudo());
    private final Account account = identity.add("valentin", "v@exemple.ca", AccountStatus.ACTIVE);

    private static EmailAddress email(String raw) {
        return EmailAddress.parse(raw).orElseThrow();
    }

    private static void assertRefused(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        SealException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    @DisplayName(
            "ACC-02 : sous sudo, un code part au nouveau courriel, valable 10 minutes ; le courriel ne change pas encore")
    void acc02SendsACodeToTheNewAddress() {
        handler.request(account.id(), email("nouvelle@exemple.ca"));

        assertThat(identity.mails).containsExactly("change-code:nouvelle@exemple.ca:valentin:123456");
        InMemoryIdentity.StoredCode code = identity.lastCodeOf(account.id());
        assertThat(code.purpose()).isEqualTo(InMemoryIdentity.CHANGE);
        assertThat(code.target()).isEqualTo(email("nouvelle@exemple.ca"));
        assertThat(code.expiresAt()).isEqualTo(InMemoryIdentity.NOW.plus(CodePolicy.VALIDITY));
        assertThat(identity.accounts.get(account.id()).email()).isEqualTo(email("v@exemple.ca"));
    }

    @Test
    @DisplayName("AUTH-55, SEC-03 : hors du mode sudo, REAUTH_REQUIRED ; rien n'est émis")
    void auth55RefusedOutsideSudo() {
        identity.sudoActive = false;

        assertRefused(() -> handler.request(account.id(), email("nouvelle@exemple.ca")), ErrorCode.REAUTH_REQUIRED);
        assertThat(identity.codes).isEmpty();
        assertThat(identity.mails).isEmpty();
    }

    @Test
    @DisplayName("ACC-02, AUTH-15, ADR 0034 : adresse d'un compte actif, aucun code ne part ; son titulaire est averti")
    void acc02TakenAddressWarnsItsHolder() {
        identity.add("titulaire", "pris@exemple.ca", AccountStatus.ACTIVE);

        handler.request(account.id(), email("PRIS@exemple.ca"));

        assertThat(identity.mails).containsExactly("taken-warning:PRIS@exemple.ca");
    }

    @Test
    @DisplayName("ACC-02 : une nouvelle demande remplace le code précédent")
    void acc02NewRequestReplacesTheCode() {
        handler.request(account.id(), email("premiere@exemple.ca"));
        identity.digits.clear();
        identity.digits.add("654321");

        handler.request(account.id(), email("seconde@exemple.ca"));

        assertThat(identity.codes).hasSize(2);
        assertThat(identity.codes.getFirst().consumed()).isTrue();
        assertThat(identity.lastCodeOf(account.id()).target()).isEqualTo(email("seconde@exemple.ca"));
    }

    @Test
    @DisplayName("ACC-02 : l'adresse actuelle n'est pas un changement (VALIDATION_FAILED)")
    void acc02SameAddressIsRefused() {
        assertRefused(() -> handler.request(account.id(), email("V@exemple.ca")), ErrorCode.VALIDATION_FAILED);
        assertThat(identity.codes).isEmpty();
    }

    @Test
    @DisplayName("SEC-03 : un compte en attente ne demande pas de changement par cette voie (AUTH-17 est la sienne)")
    void sec03PendingAccountIsRefused() {
        Account pending = identity.add("attente", "a@exemple.ca", AccountStatus.PENDING_EMAIL);

        assertRefused(() -> handler.request(pending.id(), email("nouvelle@exemple.ca")), ErrorCode.FORBIDDEN);
    }
}
