package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.authentication.application.port.in.LogoutUseCase;
import fr.vlegall.seal.authentication.infrastructure.web.dto.LoginRequest;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.Unicode;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimitExceededException;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.session.SealPrincipal;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.ClientAddress;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion et déconnexion (section 14.1 du CDC, ADR 0021). Les règles d'accès sont dans {@code SecurityConfig} :
 * connexion réservée aux anonymes, déconnexion ouverte à toute session.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class SessionController {

    private final AuthenticationManager authentication;
    private final LogoutUseCase logout;
    private final RateLimiter limiter;
    private final SessionEstablisher sessions;

    public SessionController(
            AuthenticationManager authentication,
            LogoutUseCase logout,
            RateLimiter limiter,
            SessionEstablisher sessions) {
        this.authentication = authentication;
        this.logout = logout;
        this.limiter = limiter;
        this.sessions = sessions;
    }

    /**
     * AUTH-20 à AUTH-23, hors de toute transaction. Un jeton de chaque limite est pris avant la vérification, par IP
     * puis par saisie normalisée (ADR 0013, point 4). La limite des tentatives garde le sien ; un succès rend ceux des
     * échecs, qui seuls comptent pour AUTH-22 (ADR 0021).
     */
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String ip = ClientAddress.key(request.getRemoteAddr());
        String entered = Unicode.foldLogin(body.login());
        limiter.consume(RateLimit.LOGIN_ATTEMPTS_PER_IP, ip);
        limiter.consume(RateLimit.LOGIN_FAILURES_PER_IP, ip);
        try {
            limiter.consume(RateLimit.LOGIN_FAILURES_PER_LOGIN, entered);
        } catch (RateLimitExceededException e) {
            // Aucune vérification n'a lieu : la tentative ne compte pas parmi les échecs de cette IP.
            limiter.refund(RateLimit.LOGIN_FAILURES_PER_IP, ip);
            throw e;
        }
        Authentication authenticated;
        try {
            authenticated = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.login(), body.password()));
        } catch (AuthenticationException e) {
            throw new SealException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!(authenticated.getPrincipal() instanceof SealPrincipal principal)) {
            // Échec fermé : le seul fournisseur rend toujours un compte SEAL ; autre chose est une erreur de
            // programmation.
            throw new IllegalStateException("authentification sans compte SEAL");
        }
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_IP, ip);
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_LOGIN, entered);
        UserId user = principal.userId();
        boolean verified = authenticated.getAuthorities().stream()
                .anyMatch(authority -> SealAuthorities.ACCOUNT_ACTIVE.equals(authority.getAuthority()));
        if (verified) {
            sessions.openFull(user, request, response);
        } else {
            sessions.openRestricted(user, request, response);
        }
    }

    /**
     * AUTH-53 : la session est invalidée en base, ses cookies expirés, puis le geste est journalisé : un journal
     * indisponible ne laisse pas la session ouverte.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CurrentUser UserId user, HttpServletRequest request, HttpServletResponse response) {
        sessions.close(request, response);
        logout.logout(user);
    }
}
