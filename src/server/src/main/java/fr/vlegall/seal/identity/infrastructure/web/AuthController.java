package fr.vlegall.seal.identity.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.CurrentAccountQuery;
import fr.vlegall.seal.identity.application.port.in.RegisterUseCase;
import fr.vlegall.seal.identity.application.port.in.UsernameAvailabilityQuery;
import fr.vlegall.seal.identity.infrastructure.web.dto.MeResponse;
import fr.vlegall.seal.identity.infrastructure.web.dto.RegisterRequest;
import fr.vlegall.seal.identity.infrastructure.web.dto.UsernameAvailabilityResponse;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimited;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inscription, disponibilité de l'identifiant et compte courant (section 14.1 du CDC, ADR 0019). Les règles d'accès
 * sont dans {@code SecurityConfig} ; les handlers vérifient à nouveau l'état du compte (SEC-03).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUseCase register;
    private final UsernameAvailabilityQuery usernameAvailability;
    private final CurrentAccountQuery currentAccount;
    private final SessionEstablisher sessions;

    public AuthController(
            RegisterUseCase register,
            UsernameAvailabilityQuery usernameAvailability,
            CurrentAccountQuery currentAccount,
            SessionEstablisher sessions) {
        this.register = register;
        this.usernameAvailability = usernameAvailability;
        this.currentAccount = currentAccount;
        this.sessions = sessions;
    }

    /** Compte de la session, relu en base, et facteurs obtenus par la session. */
    @GetMapping("/me")
    public MeResponse me(@CurrentUser UserId user, Authentication authentication) {
        return MeResponse.of(currentAccount.current(user), sessions.factors(authentication));
    }

    /** AUTH-10, AUTH-16 : le compte créé ouvre une session restreinte, avec un nouvel identifiant de session. */
    @PostMapping("/register")
    @RateLimited(RateLimit.REGISTRATIONS_PER_IP)
    public ResponseEntity<MeResponse> register(
            @Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        UserId user = register.register(body.toCommand());
        Authentication authentication = sessions.openRestricted(user, request, response);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MeResponse.of(currentAccount.current(user), sessions.factors(authentication)));
    }

    /** AUTH-05. */
    @GetMapping("/username-availability")
    @RateLimited(RateLimit.USERNAME_CHECKS_PER_IP)
    public UsernameAvailabilityResponse usernameAvailability(@RequestParam("u") @Size(max = 64) String candidate) {
        return UsernameAvailabilityResponse.of(usernameAvailability.check(candidate));
    }
}
