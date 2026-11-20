package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.authentication.application.port.in.CompleteOAuthRegistrationUseCase;
import fr.vlegall.seal.authentication.application.port.in.OAuthProvidersQuery;
import fr.vlegall.seal.authentication.domain.model.OAuthRegistration;
import fr.vlegall.seal.authentication.domain.model.PendingOAuthRegistration;
import fr.vlegall.seal.authentication.infrastructure.security.PendingOAuthSession;
import fr.vlegall.seal.authentication.infrastructure.web.dto.CompleteOAuthRequest;
import fr.vlegall.seal.authentication.infrastructure.web.dto.PendingRegistrationResponse;
import fr.vlegall.seal.authentication.infrastructure.web.dto.ProvidersResponse;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimited;
import fr.vlegall.seal.security.session.SessionEstablisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connexion OAuth vue de l'API (section 14.1 du CDC). Le départ et le retour sont les filtres de Spring Security ; les
 * règles d'accès sont dans {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class OAuthController {

    private final OAuthProvidersQuery providers;
    private final CompleteOAuthRegistrationUseCase registrations;
    private final PendingOAuthSession pending;
    private final SessionEstablisher sessions;

    public OAuthController(
            OAuthProvidersQuery providers,
            CompleteOAuthRegistrationUseCase registrations,
            PendingOAuthSession pending,
            SessionEstablisher sessions) {
        this.providers = providers;
        this.registrations = registrations;
        this.pending = pending;
        this.sessions = sessions;
    }

    /** AUTH-35, OPS-03 : fournisseurs configurés ; le client masque les autres boutons. */
    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return ProvidersResponse.of(providers.configured());
    }

    /** AUTH-32 : l'inscription OAuth en attente de la session, réservée aux anonymes ; 404 sans attente vivante. */
    @GetMapping("/oauth/pending")
    public PendingRegistrationResponse pending(HttpServletRequest request) {
        return PendingRegistrationResponse.of(livePending(request));
    }

    /**
     * AUTH-32 : identifiant et nom affiché choisis, le compte est créé et lié, puis la session s'ouvre sous un nouvel
     * identifiant : complète si le courriel est vérifié, restreinte sinon (ADR 0032, point 1). L'attente ne sert qu'une
     * fois ; après un refus, elle reste pour un autre choix. Le client relit ensuite {@code GET /me}.
     */
    @PostMapping("/oauth/complete")
    @RateLimited(RateLimit.REGISTRATIONS_PER_IP)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(
            @Valid @RequestBody CompleteOAuthRequest body, HttpServletRequest request, HttpServletResponse response) {
        PendingOAuthRegistration registration = livePending(request);
        OAuthRegistration created =
                registrations.complete(registration.identity(), body.username(), body.displayName());
        pending.forget(request);
        sessions.openWithAuthorizationCode(created.user(), created.active(), request, response);
    }

    private PendingOAuthRegistration livePending(HttpServletRequest request) {
        return pending.find(request).orElseThrow(() -> new SealException(ErrorCode.NOT_FOUND));
    }
}
