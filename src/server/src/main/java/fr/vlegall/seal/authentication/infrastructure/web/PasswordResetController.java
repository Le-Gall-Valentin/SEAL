package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.authentication.application.port.in.InspectPasswordResetQuery;
import fr.vlegall.seal.authentication.application.port.in.RequestPasswordResetUseCase;
import fr.vlegall.seal.authentication.application.port.in.ResetPasswordUseCase;
import fr.vlegall.seal.authentication.domain.model.ResetOutcome;
import fr.vlegall.seal.authentication.infrastructure.web.dto.PasswordResetConfirmRequest;
import fr.vlegall.seal.authentication.infrastructure.web.dto.PasswordResetInspectRequest;
import fr.vlegall.seal.authentication.infrastructure.web.dto.PasswordResetRequest;
import fr.vlegall.seal.authentication.infrastructure.web.dto.PasswordResetView;
import fr.vlegall.seal.common.BackgroundTasks;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.Unicode;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimitToken;
import fr.vlegall.seal.security.ratelimit.RateLimiter;
import fr.vlegall.seal.security.web.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mot de passe oublié (AUTH-70, ADR 0022). Les trois routes sont réservées aux anonymes, dans {@code SecurityConfig}.
 * Hors de toute transaction : la limitation de débit et le hachage la refusent.
 */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
public class PasswordResetController {

    private final RequestPasswordResetUseCase requests;
    private final InspectPasswordResetQuery inspections;
    private final ResetPasswordUseCase resets;
    private final RateLimiter limiter;
    private final BackgroundTasks background;

    public PasswordResetController(
            RequestPasswordResetUseCase requests,
            InspectPasswordResetQuery inspections,
            ResetPasswordUseCase resets,
            RateLimiter limiter,
            BackgroundTasks background) {
        this.requests = requests;
        this.inspections = inspections;
        this.resets = resets;
        this.limiter = limiter;
        this.background = background;
    }

    /**
     * Toujours 204, que le compte existe ou non (AUTH-70). 3 demandes par heure par IP, puis par courriel saisi,
     * normalisé comme pour la recherche du compte (section 15.2, ADR 0022 point 7). La demande est traitée en
     * arrière-plan : le temps de réponse ne dépend pas de l'existence du compte (ADR 0022, mise à jour).
     */
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        limiter.consumeAll(List.of(
                new RateLimitToken(RateLimit.PASSWORD_RESETS_PER_IP, ClientAddress.key(request.getRemoteAddr())),
                new RateLimitToken(RateLimit.PASSWORD_RESETS_PER_EMAIL, Unicode.foldLogin(body.email()))));
        String email = body.email();
        background.submit(() -> requests.request(email));
    }

    /** ADR 0022, point 1 : 200 pour un lien valable, 422 {@code CODE_EXPIRED} sinon, quelle qu'en soit la raison. */
    @PostMapping("/inspect")
    public PasswordResetView inspect(@Valid @RequestBody PasswordResetInspectRequest body) {
        return inspections
                .inspect(body.token())
                .map(PasswordResetView::of)
                .orElseThrow(() -> new SealException(ErrorCode.CODE_EXPIRED));
    }

    /** ADR 0022, point 6 : 204 sans session ; 422 {@code CODE_EXPIRED} ou {@code PASSWORD_TOO_WEAK}. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody PasswordResetConfirmRequest body) {
        switch (resets.reset(body.token(), body.password())) {
            case ResetOutcome.Done done -> {}
            case ResetOutcome.InvalidToken invalid -> throw new SealException(ErrorCode.CODE_EXPIRED);
            case ResetOutcome.WeakPassword weak -> throw new SealException(ErrorCode.PASSWORD_TOO_WEAK);
        }
    }
}
