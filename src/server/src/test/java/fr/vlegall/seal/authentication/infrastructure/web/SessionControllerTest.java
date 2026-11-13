package fr.vlegall.seal.authentication.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import fr.vlegall.seal.authentication.application.port.in.LogoutUseCase;
import fr.vlegall.seal.authentication.infrastructure.web.dto.LoginRequest;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimitExceededException;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.security.session.SessionEstablisher;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Ordres que les tests d'intégration n'observent pas : prise des jetons, échec fermé, déconnexion (ADR 0021). */
class SessionControllerTest {

    private static final String IP = "203.0.113.7";

    private final AuthenticationManager authentication = mock(AuthenticationManager.class);
    private final LogoutUseCase logout = mock(LogoutUseCase.class);
    private final RateLimiter limiter = mock(RateLimiter.class);
    private final SessionEstablisher sessions = mock(SessionEstablisher.class);
    private final SessionController controller = new SessionController(authentication, logout, limiter, sessions);

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    SessionControllerTest() {
        request.setRemoteAddr(IP);
    }

    private void login() {
        controller.login(new LoginRequest("Valentin", "fjord-tuque-raquette"), request, response);
    }

    @Test
    @DisplayName("SEC-10 : la limite des tentatives se prend avant celles des échecs, par IP puis par identifiant")
    void sec10AttemptsLimitIsConsumedFirst() {
        when(authentication.authenticate(any())).thenReturn(authenticated());

        login();

        InOrder order = inOrder(limiter);
        order.verify(limiter).consume(RateLimit.LOGIN_ATTEMPTS_PER_IP, IP);
        order.verify(limiter).consume(RateLimit.LOGIN_FAILURES_PER_IP, IP);
        order.verify(limiter).consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "valentin");
    }

    @Test
    @DisplayName("SEC-10 : un refus par identifiant rend les jetons déjà pris par IP, sans vérification")
    void sec10LoginLimitRefusalRefundsTheIpTokens() {
        doThrow(refused(RateLimit.LOGIN_FAILURES_PER_LOGIN))
                .when(limiter)
                .consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "valentin");

        assertThatExceptionOfType(RateLimitExceededException.class).isThrownBy(this::login);

        verify(limiter).refund(RateLimit.LOGIN_ATTEMPTS_PER_IP, IP);
        verify(limiter).refund(RateLimit.LOGIN_FAILURES_PER_IP, IP);
        verifyNoInteractions(authentication, sessions);
    }

    @Test
    @DisplayName("SEC-10 : un refus des échecs par IP rend le jeton des tentatives, sans toucher à l'identifiant")
    void sec10IpFailureLimitRefusalRefundsTheAttempt() {
        doThrow(refused(RateLimit.LOGIN_FAILURES_PER_IP)).when(limiter).consume(RateLimit.LOGIN_FAILURES_PER_IP, IP);

        assertThatExceptionOfType(RateLimitExceededException.class).isThrownBy(this::login);

        verify(limiter).refund(RateLimit.LOGIN_ATTEMPTS_PER_IP, IP);
        verify(limiter, never()).consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, "valentin");
        verify(limiter, never()).refund(RateLimit.LOGIN_FAILURES_PER_IP, IP);
        verifyNoInteractions(authentication, sessions);
    }

    @Test
    @DisplayName("SEC-10 : un refus de la limite des tentatives ne rend rien et ne prend rien d'autre")
    void sec10AttemptsLimitRefusalTakesNothingElse() {
        doThrow(refused(RateLimit.LOGIN_ATTEMPTS_PER_IP)).when(limiter).consume(RateLimit.LOGIN_ATTEMPTS_PER_IP, IP);

        assertThatExceptionOfType(RateLimitExceededException.class).isThrownBy(this::login);

        verify(limiter).consume(RateLimit.LOGIN_ATTEMPTS_PER_IP, IP);
        verifyNoMoreInteractions(limiter);
        verifyNoInteractions(authentication, sessions);
    }

    @Test
    @DisplayName("AUTH-22 : une session qui ne s'ouvre pas ne rend aucun jeton (échec fermé)")
    void auth22FailedSessionRefundsNothing() {
        Authentication authenticated = authenticated();
        when(authentication.authenticate(any())).thenReturn(authenticated);
        when(sessions.open(authenticated, request, response)).thenThrow(new IllegalStateException("session"));

        assertThatIllegalStateException().isThrownBy(this::login);

        verify(limiter, never()).refund(any(RateLimit.class), anyString());
    }

    @Test
    @DisplayName("AUTH-53 : la session est fermée avant la journalisation, qui peut échouer")
    void auth53SessionIsClosedBeforeTheJournal() {
        UserId user = new UserId(UUID.randomUUID());
        doThrow(new IllegalStateException("journal")).when(logout).logout(user);

        assertThatIllegalStateException().isThrownBy(() -> controller.logout(user, request, response));

        InOrder order = inOrder(sessions, logout);
        order.verify(sessions).close(request, response);
        order.verify(logout).logout(user);
    }

    private static RateLimitExceededException refused(RateLimit limit) {
        return new RateLimitExceededException(limit, Duration.ofMinutes(15));
    }

    private static Authentication authenticated() {
        return UsernamePasswordAuthenticationToken.authenticated("valentin", null, List.of());
    }
}
