package fr.vlegall.seal.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(OutputCaptureExtension.class)
class RateLimiterTest {

    private final MutableClock clock = MutableClock.fixedAt(Instant.parse("2026-11-07T12:00:00Z"));
    private final SecurityJournal journal = mock(SecurityJournal.class);
    private final RateLimiter limiter = new RateLimiter(clock, journal);

    private void consumeTimes(RateLimit limit, String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.consume(limit, key);
        }
    }

    private static RateLimitExceededException refusal(Runnable attempt) {
        return catchThrowableOfType(RateLimitExceededException.class, attempt::run);
    }

    private SecurityEvent onlyRecordedEvent() {
        ArgumentCaptor<SecurityEvent> recorded = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(journal).recordRefusal(recorded.capture());
        return recorded.getValue();
    }

    @Test
    @DisplayName("SEC-10 : les limites du lot 1 sont celles de la section 15.2, avec la nature de leur clé")
    void sec10LimitsMatchTheSpecification() {
        assertThat(RateLimit.LOGIN_FAILURES_PER_LOGIN)
                .returns(5, RateLimit::capacity)
                .returns(Duration.ofMinutes(15), RateLimit::period)
                .returns(RateLimitKind.ENTERED, RateLimit::kind);
        assertThat(RateLimit.LOGIN_FAILURES_PER_IP)
                .returns(20, RateLimit::capacity)
                .returns(Duration.ofMinutes(15), RateLimit::period)
                .returns(RateLimitKind.IP, RateLimit::kind);
        assertThat(RateLimit.REGISTRATIONS_PER_IP)
                .returns(5, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period)
                .returns(RateLimitKind.IP, RateLimit::kind);
        assertThat(RateLimit.USERNAME_CHECKS_PER_IP)
                .returns(30, RateLimit::capacity)
                .returns(Duration.ofMinutes(1), RateLimit::period)
                .returns(RateLimitKind.IP, RateLimit::kind);
        assertThat(RateLimit.EMAIL_CODE_RESENDS_PER_USER)
                .returns(3, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period)
                .returns(RateLimitKind.USER, RateLimit::kind);
        assertThat(RateLimit.PASSWORD_RESETS_PER_EMAIL)
                .returns(3, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period)
                .returns(RateLimitKind.EMAIL, RateLimit::kind);
        assertThat(RateLimit.PASSWORD_RESETS_PER_IP)
                .returns(3, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period)
                .returns(RateLimitKind.IP, RateLimit::kind);
    }

    @Test
    @DisplayName("SEC-10, AUTH-22 : la sixième tentative est refusée, avec le délai avant la prochaine")
    void sec10SixthAttemptIsRefusedWithRetryAfter() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);

        RateLimitExceededException refused = refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));

        assertThat(refused).isNotNull();
        assertThat(refused.code()).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThat(refused.retryAfter()).contains(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("SEC-10 : le seau se remplit de nouveau une fois la période écoulée")
    void sec10BucketRefillsAfterThePeriod() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);
        clock.advance(Duration.ofMinutes(15));

        assertThatNoException().isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));
    }

    @Test
    @DisplayName("SEC-10 : chaque clé et chaque limite a son propre seau")
    void sec10KeysAndLimitsAreIndependent() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);

        assertThatNoException().isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "alice"));
        assertThatNoException().isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_IP, "bob"));
    }

    @Test
    @DisplayName("AUTH-22 : un jeton réservé puis rendu après un succès n'est pas compté comme un échec")
    void auth22RefundGivesBackTheReservedToken() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);

        limiter.refund(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");

        assertThatNoException().isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));
        assertThat(refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob")))
                .isNotNull();
    }

    @Test
    @DisplayName("SEC-10 : rendre un jeton ne dépasse jamais la capacité du seau")
    void sec10RefundNeverExceedsCapacity() {
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");

        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);
        assertThat(refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob")))
                .isNotNull();
    }

    @Test
    @DisplayName("SEC-10 : rendre un jeton pour une clé inconnue ne crée aucun seau")
    void sec10RefundOfAnUnknownKeyCreatesNoBucket() {
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_LOGIN, "inconnu");

        assertThat(limiter.bucketCount()).isZero();
    }

    @Test
    @DisplayName("SEC-10 : le limiteur refuse de tourner dans une transaction, qui garderait une connexion (ADR 0013)")
    void adr0013LimiterRefusesToRunInsideATransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatIllegalStateException()
                    .isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));
            assertThatIllegalStateException()
                    .isThrownBy(() -> limiter.refund(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("SEC-10 : la purge retire les seaux redevenus pleins et garde les seaux entamés")
    void sec10PurgeRemovesOnlyRefilledBuckets() {
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "alice");
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");
        clock.advance(Duration.ofMinutes(15));
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");

        limiter.purgeRefilledBuckets();

        assertThat(limiter.bucketCount()).isOne();
        // Bob est gardé avec ses 4 jetons : une purge inversée l'aurait remis à 5.
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 4);
        assertThat(refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob")))
                .isNotNull();
    }

    @Test
    @DisplayName("SEC-10 : une clé saisie, même énorme, occupe une place fixe (ADR 0013)")
    void adr0013BucketKeyHasAFixedSizeWhateverTheInput() {
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "x".repeat(1_000_000));

        assertThat(limiter.bucketIds())
                .singleElement()
                .satisfies(id -> assertThat(id.length()).isLessThan(100));
    }

    @Test
    @DisplayName(
            "SEC-10 : au-delà du plafond de seaux, une nouvelle clé est refusée et l'alerte écrite une fois (ADR 0013)")
    void adr0013CapacityIsBoundedAndFailsClosed() {
        RateLimiter small = new RateLimiter(clock, journal, 2);
        small.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "alice");
        small.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");

        RateLimitExceededException refused = refusal(() -> small.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "carole"));
        refusal(() -> small.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "david"));

        assertThat(refused).isNotNull();
        assertThat(refused.retryAfter()).contains(Duration.ofMinutes(1));
        assertThatNoException().isThrownBy(() -> small.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "alice"));
        assertThat(onlyRecordedEvent().details()).containsEntry("reason", "capacity");
    }

    @Test
    @DisplayName("SEC-20 : seul le premier refus d'une période est journalisé (ADR 0013)")
    void adr0013OnlyTheFirstRefusalOfAPeriodIsJournaled() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);
        for (int i = 0; i < 10; i++) {
            refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));
        }
        verify(journal, times(1)).recordRefusal(any());

        clock.advance(Duration.ofMinutes(15));
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);
        refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));

        verify(journal, times(2)).recordRefusal(any());
    }

    @Test
    @DisplayName("SEC-20 : un dépassement par utilisateur est rattaché à cet utilisateur")
    void sec20UserLimitRecordsTheUser() {
        UserId user = new UserId(UUID.fromString("0190f1d2-7e3a-7b4c-8d5e-6f7a8b9c0d1e"));
        for (int i = 0; i < 3; i++) {
            limiter.consume(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user);
        }

        refusal(() -> limiter.consume(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user));

        SecurityEvent event = onlyRecordedEvent();
        assertThat(event.userId()).isEqualTo(user);
        assertThat(event.details()).containsEntry("limit", "EMAIL_CODE_RESENDS_PER_USER");
    }

    @Test
    @DisplayName("SEC-10 : une limite par utilisateur exige un utilisateur, et inversement (ADR 0013)")
    void adr0013KeyTypeMatchesTheLimitKind() {
        UserId user = new UserId(UUID.randomUUID());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> limiter.consume(RateLimit.EMAIL_CODE_RESENDS_PER_USER, "bob"));
        assertThatIllegalArgumentException().isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_IP, user));
    }

    @Test
    @DisplayName("SEC-21 : la clé journalisée est filtrée selon sa nature")
    void sec21RecordedKeyDependsOnItsKind() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob@exemple.ca", 5);
        refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob@exemple.ca"));
        consumeTimes(RateLimit.PASSWORD_RESETS_PER_EMAIL, "Tr0ub4dor@Horse-Batt", 3);
        refusal(() -> limiter.consume(RateLimit.PASSWORD_RESETS_PER_EMAIL, "Tr0ub4dor@Horse-Batt"));
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_IP, "2001:db8::1", 20);
        refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_IP, "2001:db8::1"));

        ArgumentCaptor<SecurityEvent> recorded = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(journal, times(3)).recordRefusal(recorded.capture());
        assertThat(recorded.getAllValues())
                .extracting(event -> event.details().get("key"))
                .containsExactly("b***@exemple.ca", "***", "2001:db8::1");
    }

    @Test
    @DisplayName(
            "SEC-21 : une saisie qui n'est pas un courriel n'est jamais journalisée, même en forme d'identifiant (ADR 0013)")
    void sec21EnteredKeyThatIsNotAnEmailIsMasked() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "monmotdepassesecret", 5);
        refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "monmotdepassesecret"));

        ArgumentCaptor<SecurityEvent> recorded = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(journal).recordRefusal(recorded.capture());
        assertThat(recorded.getValue().details()).containsEntry("key", "***");
    }

    @Test
    @DisplayName("SEC-20 : l'alerte est écrite avant le journal, et la limite tient même si le journal échoue (A09)")
    void a09WarningSurvivesAJournalFailure(CapturedOutput output) {
        doThrow(new IllegalStateException("base indisponible")).when(journal).recordRefusal(any());
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);

        RateLimitExceededException refused = refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));

        assertThat(refused).isNotNull();
        assertThat(output).contains("Limite de débit dépassée");
    }

    @Test
    @DisplayName("SEC-10 : aucune consommation réussie n'est journalisée")
    void sec10SuccessfulConsumptionIsNotJournaled() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);

        verifyNoInteractions(journal);
    }

    @Test
    @DisplayName(
            "AUTH-14 : les renvois restants partent de la capacité, sans créer de seau, et baissent à chaque renvoi")
    void auth14RemainingStartsAtCapacityAndDecreases() {
        UserId user = new UserId(UUID.fromString("0190f1d2-7e3a-7b4c-8d5e-6f7a8b9c0d1f"));

        assertThat(limiter.remaining(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user))
                .isEqualTo(3);
        assertThat(limiter.bucketCount()).isZero();
        for (int expected = 2; expected >= 0; expected--) {
            limiter.consume(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user);
            assertThat(limiter.remaining(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user))
                    .isEqualTo(expected);
        }
        clock.advance(Duration.ofHours(1));
        assertThat(limiter.remaining(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user))
                .isEqualTo(3);
    }
}
