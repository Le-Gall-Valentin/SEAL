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
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * AUTH-52 : une session expire 12 heures après sa création, même active, renommages compris (ADR 0045). Spring Session
 * ne gère que l'inactivité (30 minutes) ; ce filtre, placé avant le chargement du contexte de sécurité, invalide la
 * session trop ancienne et laisse la requête continuer sans session.
 *
 * <p>Créé par {@code SecurityConfig}, jamais déclaré comme composant : Spring Boot l'enregistrerait une seconde fois
 * comme filtre de servlet.
 */
public final class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final Duration maxAge;
    private final ApplicationEventPublisher events;

    /** @param maxAge durée de vie maximale d'une session, {@code seal.session.absolute-timeout} (12 heures) */
    public AbsoluteSessionTimeoutFilter(Clock clock, Duration maxAge, ApplicationEventPublisher events) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        // ADR 0045 : l'âge se compte depuis la première session, que chaque migration reporte.
        if (session != null && clock.millis() - SessionOrigin.createdAt(session) >= maxAge.toMillis()) {
            String id = session.getId();
            session.invalidate();
            // TRN-12, ADR 0045 : les WebSockets de la session tombée se ferment aussitôt.
            events.publishEvent(new SessionsEndedEvent(Set.of(id)));
        }
        chain.doFilter(request, response);
    }
}
