package fr.vlegall.seal.security.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AUTH-52 : une session expire 12 heures après sa création, même active. Spring Session ne gère que l'inactivité
 * (30 minutes) ; ce filtre, placé avant le chargement du contexte de sécurité, invalide la session trop ancienne et
 * laisse la requête continuer sans session.
 *
 * <p>Créé par {@code SecurityConfig}, jamais déclaré comme composant : Spring Boot l'enregistrerait une seconde fois
 * comme filtre de servlet.
 */
public final class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final Duration maxAge;

    /** @param maxAge durée de vie maximale d'une session, {@code seal.session.absolute-timeout} (12 heures) */
    public AbsoluteSessionTimeoutFilter(Clock clock, Duration maxAge) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && clock.millis() - session.getCreationTime() >= maxAge.toMillis()) {
            session.invalidate();
        }
        chain.doFilter(request, response);
    }
}
