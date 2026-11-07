package fr.vlegall.seal.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RateLimiterTest {

    private final MutableClock clock = MutableClock.fixedAt(Instant.parse("2026-11-07T12:00:00Z"));
    private final SecurityJournal journal = mock(SecurityJournal.class);
    private final RateLimiter limiter = new RateLimiter(clock, journal);

    private void consumeTimes(RateLimit limit, String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.consume(limit, key);
        }
    }

    private RateLimitExceededException refusal(Runnable attempt) {
        return catchThrowableOfType(RateLimitExceededException.class, attempt::run);
    }

    @Test
    @DisplayName("SEC-10 : les limites du lot 1 sont celles de la section 15.2")
    void sec10LimitsMatchTheSpecification() {
        assertThat(RateLimit.LOGIN_FAILURES_PER_LOGIN)
                .returns(5, RateLimit::capacity)
                .returns(Duration.ofMinutes(15), RateLimit::period);
        assertThat(RateLimit.LOGIN_FAILURES_PER_IP)
                .returns(20, RateLimit::capacity)
                .returns(Duration.ofMinutes(15), RateLimit::period);
        assertThat(RateLimit.REGISTRATIONS_PER_IP)
                .returns(5, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period);
        assertThat(RateLimit.USERNAME_CHECKS_PER_IP)
                .returns(30, RateLimit::capacity)
                .returns(Duration.ofMinutes(1), RateLimit::period);
        assertThat(RateLimit.EMAIL_CODE_RESENDS_PER_USER)
                .returns(3, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period);
        assertThat(RateLimit.PASSWORD_RESETS_PER_EMAIL)
                .returns(3, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period);
        assertThat(RateLimit.PASSWORD_RESETS_PER_IP)
                .returns(3, RateLimit::capacity)
                .returns(Duration.ofHours(1), RateLimit::period);
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
    void keysAndLimitsAreIndependent() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);

        assertThatNoException().isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "alice"));
        assertThatNoException().isThrownBy(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_IP, "bob"));
    }

    @Test
    @DisplayName("SEC-10 : vérifier une limite ne consomme rien, mais refuse une limite atteinte")
    void ensureAvailableDoesNotConsume() {
        for (int i = 0; i < 10; i++) {
            limiter.ensureAvailable(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");
        }
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob", 5);

        RateLimitExceededException refused =
                refusal(() -> limiter.ensureAvailable(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob"));
        assertThat(refused).isNotNull();
        assertThat(refused.retryAfter()).contains(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("SEC-10 : la purge retire les seaux redevenus pleins et garde les seaux entamés")
    void purgeRemovesOnlyRefilledBuckets() {
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "alice");
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");
        clock.advance(Duration.ofMinutes(15));
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob");

        limiter.purgeRefilledBuckets();

        assertThat(limiter.bucketCount()).isOne();
    }

    @Test
    @DisplayName("SEC-10, SEC-21 : un dépassement est journalisé, avec la clé masquée")
    void sec10ExceedingIsJournaledWithAMaskedKey() {
        consumeTimes(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob@exemple.ca", 5);
        verifyNoInteractions(journal);

        refusal(() -> limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "bob@exemple.ca"));

        ArgumentCaptor<SecurityEvent> recorded = ArgumentCaptor.forClass(SecurityEvent.class);
        verify(journal).record(recorded.capture());
        assertThat(recorded.getValue().type()).isEqualTo(SecurityEventType.RATE_LIMIT_EXCEEDED);
        assertThat(recorded.getValue().userId()).isNull();
        assertThat(recorded.getValue().details())
                .isEqualTo(Map.of("limit", "LOGIN_FAILURES_PER_LOGIN", "key", "b***@exemple.ca"));
    }
}
