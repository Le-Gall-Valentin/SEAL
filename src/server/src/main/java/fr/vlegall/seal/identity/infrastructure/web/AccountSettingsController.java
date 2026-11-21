package fr.vlegall.seal.identity.infrastructure.web;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.CurrentAccountQuery;
import fr.vlegall.seal.identity.application.port.in.UpdateProfileUseCase;
import fr.vlegall.seal.identity.infrastructure.web.dto.MeResponse;
import fr.vlegall.seal.identity.infrastructure.web.dto.UpdateProfileRequest;
import fr.vlegall.seal.security.session.SessionEstablisher;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final CurrentAccountQuery currentAccount;
    private final SessionEstablisher sessions;

    public AccountSettingsController(
            UpdateProfileUseCase profile, CurrentAccountQuery currentAccount, SessionEstablisher sessions) {
        this.profile = profile;
        this.currentAccount = currentAccount;
        this.sessions = sessions;
    }

    /** ACC-03 : rend le compte courant, comme {@code GET /api/v1/auth/me} (ADR 0034). */
    @PatchMapping("/profile")
    public MeResponse profile(
            @CurrentUser UserId user, @Valid @RequestBody UpdateProfileRequest body, Authentication authentication) {
        profile.changeDisplayName(user, body.toDisplayName());
        return MeResponse.of(currentAccount.current(user), sessions.factors(authentication));
    }
}
