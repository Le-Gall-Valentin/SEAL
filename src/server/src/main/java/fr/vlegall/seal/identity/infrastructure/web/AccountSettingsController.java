package fr.vlegall.seal.identity.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.ConfirmEmailChangeUseCase;
import fr.vlegall.seal.identity.application.port.in.CurrentAccountQuery;
import fr.vlegall.seal.identity.application.port.in.RequestEmailChangeUseCase;
import fr.vlegall.seal.identity.application.port.in.UpdateProfileUseCase;
import fr.vlegall.seal.identity.domain.model.EmailChangeConfirmation;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.infrastructure.web.dto.ChangeEmailRequest;
import fr.vlegall.seal.identity.infrastructure.web.dto.MeResponse;
import fr.vlegall.seal.identity.infrastructure.web.dto.UpdateProfileRequest;
import fr.vlegall.seal.identity.infrastructure.web.dto.VerifyEmailRequest;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.session.SudoMode;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paramètres du compte qui relèvent d'{@code identity} : nom affiché et courriel (section 14.2 du CDC, ADR 0034). Les
 * règles d'accès sont dans {@code SecurityConfig} (session complète) ; les handlers vérifient à nouveau l'état du compte
 * (SEC-03).
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountSettingsController {

    private final UpdateProfileUseCase profile;
    private final RequestEmailChangeUseCase emailChange;
    private final ConfirmEmailChangeUseCase emailConfirmation;
    private final CurrentAccountQuery currentAccount;
    private final SessionEstablisher sessions;
    private final SudoMode sudo;
    private final RateLimiter limiter;

    public AccountSettingsController(
            UpdateProfileUseCase profile,
            RequestEmailChangeUseCase emailChange,
            ConfirmEmailChangeUseCase emailConfirmation,
            CurrentAccountQuery currentAccount,
            SessionEstablisher sessions,
            SudoMode sudo,
            RateLimiter limiter) {
        this.profile = profile;
        this.emailChange = emailChange;
        this.emailConfirmation = emailConfirmation;
        this.currentAccount = currentAccount;
        this.sessions = sessions;
        this.sudo = sudo;
        this.limiter = limiter;
    }

    /** ACC-03 : rend le compte courant, comme {@code GET /api/v1/auth/me} (ADR 0034). */
    @PatchMapping("/profile")
    public MeResponse profile(
            @CurrentUser UserId user, @Valid @RequestBody UpdateProfileRequest body, Authentication authentication) {
        profile.changeDisplayName(user, body.toDisplayName());
        return MeResponse.of(currentAccount.current(user), sessions.factors(authentication));
    }

    /**
     * ACC-02 : 204 que l'adresse soit prise ou non (ADR 0034, point 3). Le mode sudo est vérifié avant de prendre un
     * jeton des 3 envois par heure (point 4) : une demande refusée faute de ré-authentification n'en consomme pas. Le
     * handler le vérifie de nouveau (SEC-03).
     */
    @PostMapping("/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailChange(
            @CurrentUser UserId user, @Valid @RequestBody ChangeEmailRequest body, Authentication authentication) {
        sudo.require(authentication);
        limiter.consume(RateLimit.EMAIL_CODE_RESENDS_PER_USER, user);
        emailChange.request(user, body.toEmail());
    }

    /** ACC-02 : rend le compte courant, comme {@code GET /api/v1/auth/me}. */
    @PostMapping("/email/confirm")
    public MeResponse confirmEmailChange(
            @CurrentUser UserId user, @Valid @RequestBody VerifyEmailRequest body, Authentication authentication) {
        return switch (emailConfirmation.confirm(user, body.toCode())) {
            case EmailChangeConfirmation.Changed changed ->
                MeResponse.of(currentAccount.current(user), sessions.factors(authentication));
            case EmailChangeConfirmation.InvalidCode invalid -> throw new IdentityException.InvalidCode();
            case EmailChangeConfirmation.NoLiveCode none -> throw new IdentityException.CodeExpired();
        };
    }
}
