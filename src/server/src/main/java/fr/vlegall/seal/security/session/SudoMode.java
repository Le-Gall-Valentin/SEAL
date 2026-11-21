package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.SealProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * AUTH-55, ADR 0034 : mode sudo. Il est ouvert tant que la session porte au moins un facteur, mot de passe ou retour
 * OAuth, obtenu il y a moins de 10 minutes. Spring Security 7.1 sait exiger qu'un facteur soit récent
 * ({@code validDuration}), mais seulement pour tous les facteurs requis à la fois : la règle « l'un ou l'autre » est
 * donc écrite ici, avec l'horloge injectée.
 */
@Component
public final class SudoMode {

    private final Clock clock;
    private final Duration window;

    @Autowired
    public SudoMode(Clock clock, SealProperties properties) {
        this(clock, properties.session().sudoWindow());
    }

    SudoMode(Clock clock, Duration window) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.window = Objects.requireNonNull(window, "window");
    }

    /**
     * Vrai si l'authentification porte un facteur daté de moins de la durée du mode sudo. Une autorité sans date ne
     * compte pas (échec fermé).
     */
    public boolean isActive(@Nullable Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        Instant oldestAccepted = clock.instant().minus(window);
        return authentication.getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance)
                .map(FactorGrantedAuthority.class::cast)
                .anyMatch(factor -> factor.getIssuedAt().isAfter(oldestAccepted));
    }

    /** @throws SealException {@code REAUTH_REQUIRED} (403) hors du mode sudo */
    public void require(@Nullable Authentication authentication) {
        if (!isActive(authentication)) {
            throw new SealException(ErrorCode.REAUTH_REQUIRED);
        }
    }
}
