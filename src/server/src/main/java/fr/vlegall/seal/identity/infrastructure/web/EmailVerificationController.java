package fr.vlegall.seal.identity.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.ChangePendingEmailUseCase;
import fr.vlegall.seal.identity.application.port.in.CurrentAccountQuery;
import fr.vlegall.seal.identity.application.port.in.ResendCodeUseCase;
import fr.vlegall.seal.identity.application.port.in.VerifyEmailUseCase;
import fr.vlegall.seal.identity.domain.model.EmailVerification;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.infrastructure.web.dto.ChangeEmailRequest;
import fr.vlegall.seal.identity.infrastructure.web.dto.MeResponse;
import fr.vlegall.seal.identity.infrastructure.web.dto.VerifyEmailRequest;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimited;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.session.SessionRevocation;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vérification, renvoi et changement du courriel, réservés à la session restreinte (AUTH-14, AUTH-16, AUTH-17). Renvoi
 * et changement partagent la limite de 3 par heure (section 15.2 du CDC).
 */
@RestController
@RequestMapping("/api/v1/auth/verify-email")
public class EmailVerificationController {

    private final VerifyEmailUseCase verifyEmail;
    private final ResendCodeUseCase resendCode;
    private final ChangePendingEmailUseCase changeEmail;
    private final CurrentAccountQuery currentAccount;
    private final SessionEstablisher sessions;
    private final SessionRevocation revocation;

    public EmailVerificationController(
            VerifyEmailUseCase verifyEmail,
            ResendCodeUseCase resendCode,
            ChangePendingEmailUseCase changeEmail,
            CurrentAccountQuery currentAccount,
            SessionEstablisher sessions,
            SessionRevocation revocation) {
        this.verifyEmail = verifyEmail;
        this.resendCode = resendCode;
        this.changeEmail = changeEmail;
        this.currentAccount = currentAccount;
        this.sessions = sessions;
        this.revocation = revocation;
    }

    /**
     * AUTH-16 : une vérification réussie rend la session complète, avec un nouvel identifiant de session. Les autres
     * sessions du compte, toutes restreintes, sont révoquées d'abord, tant que la session courante porte encore son
     * identifiant en base (ADR 0021, point 4) : l'autre appareil se reconnecte, en session complète.
     */
    @PostMapping
    public MeResponse verify(
            @CurrentUser UserId user,
            @Valid @RequestBody VerifyEmailRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        EmailVerification result = verifyEmail.verify(user, body.toCode());
        return switch (result) {
            case EmailVerification.Verified verified -> {
                revocation.revokeOthers(user, request.getSession().getId());
                Authentication authentication = sessions.upgradeToFull(user, request, response);
                yield MeResponse.of(currentAccount.current(user), sessions.factors(authentication));
            }
            case EmailVerification.InvalidCode invalid -> throw new IdentityException.InvalidCode();
            case EmailVerification.NoLiveCode none -> throw new IdentityException.CodeExpired();
        };
    }

    @PostMapping("/resend")
    @RateLimited(RateLimit.EMAIL_CODE_RESENDS_PER_USER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resend(@CurrentUser UserId user) {
        resendCode.resend(user);
    }

    @PostMapping("/change")
    @RateLimited(RateLimit.EMAIL_CODE_RESENDS_PER_USER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@CurrentUser UserId user, @Valid @RequestBody ChangeEmailRequest body) {
        changeEmail.change(user, body.toEmail());
    }
}
