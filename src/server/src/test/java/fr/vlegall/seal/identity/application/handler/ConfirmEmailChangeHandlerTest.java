package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.EmailChangeConfirmation;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmEmailChangeHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final RequestEmailChangeHandler request = new RequestEmailChangeHandler(
            identity.users(),
            identity.emailCodes(),
            identity.mail(),
            identity.random(),
            identity.clock,
            identity.sudo());
    private final ConfirmEmailChangeHandler handler = new ConfirmEmailChangeHandler(
            identity.users(),
            identity.emailCodes(),
            identity.mail(),
            identity.journal(),
            identity.resetLinks(),
            identity.clock);
    private final Account account = identity.add("valentin", "v@exemple.ca", AccountStatus.ACTIVE);

    private static EmailAddress email(String raw) {
        return EmailAddress.parse(raw).orElseThrow();
    }

    private static VerificationCode code(String digits) {
        return VerificationCode.parse(digits).orElseThrow();
    }

    private void requested() {
        request.request(account.id(), email("nouvelle@exemple.ca"));
        identity.mails.clear();
    }

    @Test
    @DisplayName(
            "ACC-02 : le bon code remplace le courriel, journalise, prévient l'ancienne adresse et tue les liens de réinitialisation")
    void acc02RightCodeChangesTheAddress() {
        requested();

        assertThat(handler.confirm(account.id(), code("123456"))).isEqualTo(new EmailChangeConfirmation.Changed());

        assertThat(identity.accounts.get(account.id()).email()).isEqualTo(email("nouvelle@exemple.ca"));
        assertThat(identity.events)
                .containsExactly("email-changed:" + account.id().value() + ":nouvelle@exemple.ca");
        assertThat(identity.mails).containsExactly("changed-notice:v@exemple.ca:valentin:nouvelle@exemple.ca");
        assertThat(identity.resetLinksInvalidated).containsExactly(account.id());
        assertThat(identity.lastCodeOf(account.id()).consumed()).isTrue();
    }

    @Test
    @DisplayName("ACC-02 : un mauvais code compte un essai ; au cinquième, même le bon répond CODE_EXPIRED")
    void acc02FiveWrongCodesExhaustTheCode() {
        requested();

        for (int i = 0; i < 5; i++) {
            assertThat(handler.confirm(account.id(), code("000000")))
                    .isEqualTo(new EmailChangeConfirmation.InvalidCode());
        }
        assertThat(handler.confirm(account.id(), code("123456"))).isEqualTo(new EmailChangeConfirmation.NoLiveCode());
        assertThat(identity.accounts.get(account.id()).email()).isEqualTo(email("v@exemple.ca"));
    }

    @Test
    @DisplayName("ACC-02 : le code expire au bout de 10 minutes")
    void acc02CodeExpiresAfterTenMinutes() {
        requested();
        identity.clock.advance(Duration.ofMinutes(10));

        assertThat(handler.confirm(account.id(), code("123456"))).isEqualTo(new EmailChangeConfirmation.NoLiveCode());
    }

    @Test
    @DisplayName(
            "ACC-02, AUTH-15 : adresse activée entre-temps par un autre compte, code consommé, CODE_EXPIRED, rien ne change")
    void acc02AddressTakenMeanwhile() {
        requested();
        identity.add("rapide", "nouvelle@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.confirm(account.id(), code("123456"))).isEqualTo(new EmailChangeConfirmation.NoLiveCode());

        assertThat(identity.accounts.get(account.id()).email()).isEqualTo(email("v@exemple.ca"));
        assertThat(identity.lastCodeOf(account.id()).consumed()).isTrue();
        assertThat(identity.mails).isEmpty();
        assertThat(identity.resetLinksInvalidated).isEmpty();
    }

    @Test
    @DisplayName("ACC-02 : un code de vérification du courriel ne confirme pas un changement")
    void acc02VerificationCodeDoesNotConfirmAChange() {
        identity.codes.add(new InMemoryIdentity.StoredCode(
                UUID.randomUUID(),
                account.id(),
                InMemoryIdentity.VERIFY,
                null,
                code("123456").hash(),
                InMemoryIdentity.NOW,
                InMemoryIdentity.NOW.plus(Duration.ofMinutes(10)),
                0,
                false));

        assertThat(handler.confirm(account.id(), code("123456"))).isEqualTo(new EmailChangeConfirmation.NoLiveCode());
    }

    @Test
    @DisplayName("SEC-03 : un compte en attente ne confirme rien par cette voie")
    void sec03PendingAccountIsRefused() {
        Account pending = identity.add("attente", "a@exemple.ca", AccountStatus.PENDING_EMAIL);

        assertThatThrownBy(() -> handler.confirm(pending.id(), code("123456")))
                .isInstanceOfSatisfying(
                        SealException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
    }
}
