package fr.vlegall.seal.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.journal.SecurityJournal;
import fr.vlegall.seal.security.session.SealPrincipal;
import fr.vlegall.seal.support.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitedInterceptorTest {

    private final RateLimiter limiter =
            new RateLimiter(MutableClock.fixedAt(Instant.parse("2026-11-07T12:00:00Z")), mock(SecurityJournal.class));
    private RateLimitedProbe probe;

    @BeforeEach
    void setUp() {
        ProxyFactory factory = new ProxyFactory(new RateLimitedProbe());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(RateLimitConfiguration.rateLimitedAdvisor(new RateLimitedInterceptor(() -> limiter)));
        probe = (RateLimitedProbe) factory.getProxy();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(UserId user) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(new SealPrincipal(user), null, List.of()));
    }

    @Test
    @DisplayName("SEC-10 : une méthode annotée applique sa limite par IP avant de s'exécuter")
    void sec10AnnotatedMethodIsLimitedByIp() {
        for (int i = 0; i < 5; i++) {
            assertThat(probe.register()).isEqualTo("inscrit");
        }

        assertThatExceptionOfType(RateLimitExceededException.class).isThrownBy(probe::register);
    }

    @Test
    @DisplayName("SEC-10 : plusieurs annotations appliquent chacune leur limite")
    void sec10RepeatedAnnotationsAreAllApplied() {
        probe.registerAndCheck();

        assertThat(limiter.bucketIds()).hasSize(2);
    }

    @Test
    @DisplayName("SEC-10 : une méthode sans annotation n'est pas limitée")
    void sec10UnannotatedMethodIsFree() {
        probe.free();

        assertThat(limiter.bucketCount()).isZero();
    }

    @Test
    @DisplayName("ADR 0013 : sans requête en cours, l'appel est refusé plutôt que laissé sans limite")
    void adr0013NoRequestFailsClosed() {
        RequestContextHolder.resetRequestAttributes();

        assertThatIllegalStateException().isThrownBy(probe::register);
    }

    @Test
    @DisplayName(
            "AUTH-14 : une limite par utilisateur s'applique à l'utilisateur de la session ; le 4e renvoi est refusé")
    void auth14UserLimitAppliesToTheAuthenticatedUser() {
        UserId user = new UserId(UUID.fromString("0190f1d2-7e3a-7b4c-8d5e-6f7a8b9c0d1e"));
        authenticate(user);
        for (int i = 0; i < 3; i++) {
            assertThat(probe.resend()).isEqualTo("renvoyé");
        }

        assertThatExceptionOfType(RateLimitExceededException.class).isThrownBy(probe::resend);
        assertThat(limiter.remaining(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user))
                .isZero();
    }

    @Test
    @DisplayName("ADR 0013 : sans utilisateur authentifié, une limite par utilisateur refuse l'appel (échec fermé)")
    void adr0013UserLimitWithoutUserFailsClosed() {
        assertThatIllegalStateException().isThrownBy(probe::resend);

        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "clé", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThatIllegalStateException().isThrownBy(probe::resend);
        assertThat(limiter.bucketCount()).isZero();
    }
}
