package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.EmailVerification;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerifyEmailHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final VerifyEmailHandler handler =
            new VerifyEmailHandler(identity.users(), identity.emailCodes(), identity.clock);
    private final Account account = identity.add("valentin", "v@exemple.ca", AccountStatus.PENDING_EMAIL);

    private static VerificationCode code(String digits) {
        return VerificationCode.parse(digits).orElseThrow();
    }

    @Test
    @DisplayName("AUTH-16 : le bon code active le compte et consomme le code")
    void auth16RightCodeActivatesTheAccount() {
        identity.issueCode(account, "482913");

        assertThat(handler.verify(account.id(), code("482913"))).isInstanceOf(EmailVerification.Verified.class);
        assertThat(identity.accounts.get(account.id()).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(identity.lastCodeOf(account.id()).consumed()).isTrue();
    }

    @Test
    @DisplayName("AUTH-14 : un mauvais code répond InvalidCode et compte l'essai")
    void auth14WrongCodeCountsTheAttempt() {
        identity.issueCode(account, "482913");

        assertThat(handler.verify(account.id(), code("000000"))).isInstanceOf(EmailVerification.InvalidCode.class);
        assertThat(identity.lastCodeOf(account.id()).attempts()).isEqualTo(1);
        assertThat(identity.accounts.get(account.id()).status()).isEqualTo(AccountStatus.PENDING_EMAIL);
    }

    @Test
    @DisplayName("AUTH-14 : après 5 mauvais codes, même le bon répond NoLiveCode")
    void auth14FiveFailuresExhaustTheCode() {
        identity.issueCode(account, "482913");
        for (int i = 0; i < 5; i++) {
            assertThat(handler.verify(account.id(), code("00000" + i)))
                    .isInstanceOf(EmailVerification.InvalidCode.class);
        }

        assertThat(handler.verify(account.id(), code("482913"))).isInstanceOf(EmailVerification.NoLiveCode.class);
        assertThat(identity.accounts.get(account.id()).status()).isEqualTo(AccountStatus.PENDING_EMAIL);
    }

    @Test
    @DisplayName("AUTH-14 : un code vit 10 minutes, pas une de plus")
    void auth14CodeExpiresAfterTenMinutes() {
        identity.issueCode(account, "482913");
        identity.clock.advance(Duration.ofMinutes(10));

        assertThat(handler.verify(account.id(), code("482913"))).isInstanceOf(EmailVerification.NoLiveCode.class);
    }

    @Test
    @DisplayName("AUTH-14 : un renvoi remplace le code ; l'ancien ne vaut plus")
    void auth14ResendReplacesTheCode() {
        identity.issueCode(account, "111111");
        identity.issueCode(account, "222222");

        assertThat(handler.verify(account.id(), code("111111"))).isInstanceOf(EmailVerification.InvalidCode.class);
        assertThat(handler.verify(account.id(), code("222222"))).isInstanceOf(EmailVerification.Verified.class);
    }

    @Test
    @DisplayName("AUTH-15 : l'activation retire les codes des autres comptes en attente à la même adresse")
    void auth15ActivationRetiresOtherPendingCodes() {
        Account squatter = identity.add("squatteur", "V@exemple.ca", AccountStatus.PENDING_EMAIL);
        identity.issueCode(squatter, "999999");
        identity.issueCode(account, "482913");

        handler.verify(account.id(), code("482913"));

        assertThat(handler.verify(squatter.id(), code("999999"))).isInstanceOf(EmailVerification.NoLiveCode.class);
    }

    @Test
    @DisplayName("SEC-03 : un compte déjà actif n'a aucun code à vérifier")
    void sec03ActiveAccountHasNothingToVerify() {
        Account active = identity.add("actif", "a@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.verify(active.id(), code("482913"))).isInstanceOf(EmailVerification.NoLiveCode.class);
    }

    @Test
    @DisplayName("A01 : une session dont le compte n'existe pas répond UNAUTHENTICATED")
    void a01UnknownAccountIsUnauthenticated() {
        assertThatThrownBy(() -> handler.verify(new UserId(UUID.randomUUID()), code("482913")))
                .isInstanceOf(SealException.class)
                .extracting(e -> ((SealException) e).code())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}
