package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.authentication.application.port.in.ReauthenticateUseCase;
import fr.vlegall.seal.authentication.infrastructure.web.dto.ReauthRequest;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.ClientAddress;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mode sudo par mot de passe (AUTH-55, section 14.1 du CDC, ADR 0034). La règle d'accès est dans
 * {@code SecurityConfig} : session complète seulement.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class ReauthenticationController {

    private final ReauthenticateUseCase reauthentication;
    private final PasswordCheckLimits limits;
    private final SessionEstablisher sessions;

    public ReauthenticationController(
            ReauthenticateUseCase reauthentication, PasswordCheckLimits limits, SessionEstablisher sessions) {
        this.reauthentication = reauthentication;
        this.limits = limits;
        this.sessions = sessions;
    }

    /**
     * Hors de toute transaction. Un mauvais mot de passe répond 401 {@code INVALID_CREDENTIALS}, comme la connexion :
     * le client ne termine la session que sur {@code UNAUTHENTICATED}. Une réussite redate le facteur mot de passe, sous
     * un nouvel identifiant de session et un nouveau jeton CSRF.
     */
    @PostMapping("/reauth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reauth(
            @CurrentUser UserId user,
            @Valid @RequestBody ReauthRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String ip = ClientAddress.key(request.getRemoteAddr());
        limits.take(user, ip);
        if (!reauthentication.reauthenticate(user, body.password())) {
            throw new SealException(ErrorCode.INVALID_CREDENTIALS);
        }
        // La session est redatée d'abord : si elle échoue, les jetons ne sont pas rendus (échec fermé).
        sessions.refreshFactor(SealAuthorities.FACTOR_PASSWORD, request, response);
        limits.succeeded(user, ip);
    }
}
