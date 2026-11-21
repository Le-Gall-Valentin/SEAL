package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.security.session.SessionOpeningCleanup;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * ADR 0032, point 7 : une session qui s'ouvre, par mot de passe ou par OAuth, oublie le départ OAuth et l'inscription en
 * attente qu'elle portait. Ils ne serviraient plus qu'à changer le compte de la session.
 *
 * <p>Ce nettoyage ne tient pas face à deux requêtes simultanées de la même session : un départ qui finit après
 * l'ouverture y remet sa requête, Spring Session JDBC enregistrant les attributs par différence. La garantie vient
 * donc des refus : {@link OAuthAuthorizationRequests} ne rend aucune requête à une session ouverte, et les règles
 * d'accès réservent l'inscription en attente aux anonymes.
 */
@Component
final class OAuthSessionCleanup implements SessionOpeningCleanup {

    private final OAuthAuthorizationRequests requests;
    private final PendingOAuthSession pending;

    OAuthSessionCleanup(OAuthAuthorizationRequests requests, PendingOAuthSession pending) {
        this.requests = requests;
        this.pending = pending;
    }

    @Override
    public void clean(HttpServletRequest request) {
        requests.forget(request);
        pending.forget(request);
    }
}
