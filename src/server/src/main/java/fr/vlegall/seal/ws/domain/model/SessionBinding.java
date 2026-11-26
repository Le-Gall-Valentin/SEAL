package fr.vlegall.seal.ws.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TRN-12, ADR 0044 : la session HTTP d'une connexion. {@code formers} garde les identifiants d'avant les renouvellements
 * pas encore confirmés en base, du plus récent au plus ancien : une requête qui renomme n'écrit le nouvel identifiant
 * qu'à sa fin, et une ré-authentification OAuth renomme deux fois.
 */
public record SessionBinding(String current, List<String> formers) {

    /** Assez pour les deux renommages d'une même requête ; au-delà, les plus anciens sont oubliés. */
    static final int MAX_FORMERS = 4;

    public SessionBinding {
        Objects.requireNonNull(current, "current");
        formers = List.copyOf(formers);
    }

    public static SessionBinding of(String sessionId) {
        return new SessionBinding(sessionId, List.of());
    }

    /** La session a changé d'identifiant ; l'actuel rejoint les anciens. */
    public SessionBinding renamedTo(String renamed) {
        List<String> previous = new ArrayList<>(MAX_FORMERS);
        previous.add(current);
        formers.stream().limit(MAX_FORMERS - 1L).forEach(previous::add);
        return new SessionBinding(renamed, previous);
    }

    /** Le nouvel identifiant est en base : les anciens n'ont plus cours. */
    public SessionBinding settled() {
        return of(current);
    }

    /** Vrai si la connexion relève de cette session, sous son identifiant actuel ou un ancien. */
    public boolean names(String sessionId) {
        return current.equals(sessionId) || formers.contains(sessionId);
    }
}
