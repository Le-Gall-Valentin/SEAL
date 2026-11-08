package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResendAndChangeHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final ResendCodeHandler resend = new ResendCodeHandler(identity.users(), identity.codeDelivery());
    private final ChangePendingEmailHandler change =
            new ChangePendingEmailHandler(identity.users(), identity.journal(), identity.codeDelivery());
    private final Account account = identity.add("valentin", "v@exemple.ca", AccountStatus.PENDING_EMAIL);

    private static EmailAddress email(String raw) {
        return EmailAddress.parse(raw).orElseThrow();
    }

    @Test
    @DisplayName("AUTH-14 : le renvoi émet un nouveau code et retire le précédent")
    void auth14ResendIssuesANewCode() {
        identity.issueCode(account, "111111");
        identity.digits.clear();
        identity.digits.add("222222");

        resend.resend(account.id());

        assertThat(identity.codes).hasSize(2);
        assertThat(identity.codes.getFirst().consumed()).isTrue();
        assertThat(identity.mails).last().isEqualTo("code:v@exemple.ca:valentin:222222");
    }

    @Test
    @DisplayName("AUTH-17 : le changement remplace l'adresse, est journalisé et envoie le code à la nouvelle adresse")
    void auth17ChangeReplacesTheAddress() {
        change.change(account.id(), email("nouvelle@exemple.ca"));

        assertThat(identity.accounts.get(account.id()).email()).isEqualTo(email("nouvelle@exemple.ca"));
        assertThat(identity.events)
                .containsExactly("email-changed:" + account.id().value() + ":nouvelle@exemple.ca");
        assertThat(identity.mails).containsExactly("code:nouvelle@exemple.ca:valentin:123456");
    }

    @Test
    @DisplayName("AUTH-15 : changer pour l'adresse d'un compte actif avertit son titulaire, sans rien révéler")
    void auth15ChangeToAnActiveAddressWarnsTheHolder() {
        identity.add("titulaire", "pris@exemple.ca", AccountStatus.ACTIVE);

        change.change(account.id(), email("PRIS@exemple.ca"));

        assertThat(identity.accounts.get(account.id()).email()).isEqualTo(email("PRIS@exemple.ca"));
        assertThat(identity.codes).hasSize(1);
        assertThat(identity.mails).containsExactly("warning:PRIS@exemple.ca");
    }

    @Test
    @DisplayName("SEC-03 : un compte actif ne renvoie ni ne change de code (FORBIDDEN)")
    void sec03ActiveAccountIsForbidden() {
        Account active = identity.add("actif", "a@exemple.ca", AccountStatus.ACTIVE);

        assertThatThrownBy(() -> resend.resend(active.id()))
                .isInstanceOf(SealException.class)
                .extracting(e -> ((SealException) e).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> change.change(active.id(), email("b@exemple.ca")))
                .isInstanceOf(SealException.class)
                .extracting(e -> ((SealException) e).code())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(identity.mails).isEmpty();
    }
}
