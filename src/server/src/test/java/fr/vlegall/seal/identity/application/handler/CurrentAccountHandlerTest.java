package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.AccountView;
import fr.vlegall.seal.identity.domain.model.PendingVerification;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CurrentAccountHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final CurrentAccountHandler handler =
            new CurrentAccountHandler(identity.users(), identity.emailCodes(), identity.allowance(), identity.clock);

    @Test
    @DisplayName("AUTH-14 : un compte en attente reçoit l'expiration de son dernier code et ses renvois restants")
    void auth14PendingAccountGetsItsVerificationData() {
        Account account = identity.add("valentin", "v@exemple.ca", AccountStatus.PENDING_EMAIL);
        identity.emailCodes()
                .renew(
                        account.id(),
                        new byte[32],
                        InMemoryIdentity.NOW,
                        InMemoryIdentity.NOW.plus(Duration.ofMinutes(10)));
        identity.resendsRemaining.put(account.id(), 2L);

        AccountView view = handler.current(account.id());

        assertThat(view.status()).isEqualTo(AccountStatus.PENDING_EMAIL);
        assertThat(view.username().value()).isEqualTo("valentin");
        assertThat(view.verification())
                .isEqualTo(new PendingVerification(InMemoryIdentity.NOW.plus(Duration.ofMinutes(10)), 2));
    }

    @Test
    @DisplayName("AUTH-14 : sans code vivant (épuisé, consommé ou expiré), aucune expiration n'est annoncée")
    void auth14NoLiveCodeMeansNoExpiry() {
        Account exhausted = identity.add("epuise", "e@exemple.ca", AccountStatus.PENDING_EMAIL);
        identity.issueCode(exhausted, "111111");
        for (int i = 0; i < 5; i++) {
            identity.emailCodes()
                    .countFailedAttempt(identity.lastCodeOf(exhausted.id()).id());
        }
        Account expired = identity.add("perime", "p@exemple.ca", AccountStatus.PENDING_EMAIL);
        identity.issueCode(expired, "222222");

        assertThat(handler.current(exhausted.id()).verification().codeExpiresAt())
                .isNull();
        identity.clock.advance(Duration.ofMinutes(10));
        assertThat(handler.current(expired.id()).verification().codeExpiresAt()).isNull();
    }

    @Test
    @DisplayName("AUTH-16 : un compte actif n'a pas de données de vérification")
    void auth16ActiveAccountHasNoVerification() {
        Account account = identity.add("valentin", "v@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.current(account.id()).verification()).isNull();
    }

    @Test
    @DisplayName("A01 : une session dont le compte n'existe pas répond UNAUTHENTICATED (échec fermé)")
    void a01UnknownAccountIsUnauthenticated() {
        assertThatThrownBy(() -> handler.current(new UserId(UUID.randomUUID())))
                .isInstanceOf(SealException.class)
                .extracting(e -> ((SealException) e).code())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }
}
