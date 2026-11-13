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
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.ClientAddress;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
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
     * puis par saisie normalisée (ADR 0013, point 4). Une fois la vérification faite, la limite des tentatives garde
     * le sien ; un succès rend ceux des échecs, qui seuls comptent pour AUTH-22 (ADR 0021).
     */
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        String ip = ClientAddress.key(request.getRemoteAddr());
        String entered = Unicode.foldLogin(body.login());
        take(List.of(
                new Token(RateLimit.LOGIN_ATTEMPTS_PER_IP, ip),
                new Token(RateLimit.LOGIN_FAILURES_PER_IP, ip),
                new Token(RateLimit.LOGIN_FAILURES_PER_LOGIN, entered)));
        Authentication authenticated;
        try {
            authenticated = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.login(), body.password()));
        } catch (AuthenticationException e) {
            throw new SealException(ErrorCode.INVALID_CREDENTIALS);
        }
        // La session s'ouvre d'abord : si elle échoue, les jetons ne sont pas rendus (échec fermé).
        sessions.open(authenticated, request, response);
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_IP, ip);
        limiter.refund(RateLimit.LOGIN_FAILURES_PER_LOGIN, entered);
    }

    /**
     * Prend les jetons dans l'ordre. Quand une limite refuse, ceux déjà pris sont rendus : aucune vérification n'a eu
     * lieu, la requête ne compte ni comme tentative ni comme échec (SEC-10, ADR 0021).
     */
    private void take(List<Token> tokens) {
        List<Token> taken = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            try {
                limiter.consume(token.limit(), token.key());
            } catch (RateLimitExceededException e) {
                taken.forEach(t -> limiter.refund(t.limit(), t.key()));
                throw e;
            }
            taken.add(token);
        }
    }

    /** Un jeton d'une limite dont la clé est une IP ou une saisie. */
    private record Token(RateLimit limit, String key) {}

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
