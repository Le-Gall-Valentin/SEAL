package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.authentication.application.port.in.ChangePasswordUseCase;
import fr.vlegall.seal.authentication.domain.model.PasswordChangeOutcome;
import fr.vlegall.seal.authentication.infrastructure.web.dto.ChangePasswordRequest;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.ClientAddress;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mot de passe depuis les paramètres du compte (ACC-01, section 14.2 du CDC, ADR 0034). La règle d'accès est dans
 * {@code SecurityConfig} : session complète seulement. Le contexte {@code authentication} possède le mot de passe
 * (ADR 0008).
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountPasswordController {

    private final ChangePasswordUseCase passwords;
    private final PasswordCheckLimits limits;
    private final SessionEstablisher sessions;

    public AccountPasswordController(
            ChangePasswordUseCase passwords, PasswordCheckLimits limits, SessionEstablisher sessions) {
        this.passwords = passwords;
        this.limits = limits;
        this.sessions = sessions;
    }

    /**
     * Hors de toute transaction. Le mot de passe actuel compte dans les limites de la ré-authentification (ADR 0034,
     * point 4) : faux, il répond 401 {@code INVALID_CREDENTIALS}. La session courante est gardée : les autres sont
     * révoquées pendant qu'elle porte encore son identifiant, puis elle reçoit la nouvelle version, un facteur redaté
     * et un nouvel identifiant.
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(
            @CurrentUser UserId user,
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String ip = ClientAddress.key(request.getRemoteAddr());
        HttpSession current = request.getSession(false);
        if (current == null) {
            throw new SealException(ErrorCode.UNAUTHENTICATED);
        }
        limits.take(user, ip);
        switch (passwords.change(user, body.currentPassword(), body.newPassword(), body.signOut(), current.getId())) {
            case PasswordChangeOutcome.Changed changed -> {
                // La session est regravée d'abord : si elle échoue, les jetons ne sont pas rendus (échec fermé).
                sessions.passwordChanged(changed.version(), request, response);
                limits.succeeded(user, ip);
            }
            case PasswordChangeOutcome.InvalidCurrentPassword invalid ->
                throw new SealException(ErrorCode.INVALID_CREDENTIALS);
            case PasswordChangeOutcome.WeakPassword weak -> {
                // Le mot de passe actuel était le bon : l'essai ne compte pas comme un échec.
                limits.succeeded(user, ip);
                throw new SealException(ErrorCode.PASSWORD_TOO_WEAK);
            }
        }
    }
}
