package fr.vlegall.seal.ws.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * TRN-12, ADR 0044 : la session HTTP d'une connexion. {@code formers} garde les identifiants d'avant les renouvellements
 * pas encore confirmés en base, du plus récent au plus ancien : une requête qui renomme n'écrit le nouvel identifiant
 * qu'à sa fin, et plusieurs requêtes peuvent renommer avant la ronde suivante. {@code renamedAt} date le dernier
 * renommage pas encore confirmé ; nul une fois confirmé.
 */
public record SessionBinding(
        String current, List<String> formers, @Nullable Instant renamedAt) {

    /** Assez pour les renommages qui se suivent avant une ronde ; au-delà, les plus anciens sont oubliés. */
    static final int MAX_FORMERS = 4;

    public SessionBinding {
        Objects.requireNonNull(current, "current");
        formers = List.copyOf(formers);
    }

    public static SessionBinding of(String sessionId) {
        return new SessionBinding(sessionId, List.of(), null);
    }

    /** La session a changé d'identifiant à l'instant {@code at} ; l'actuel rejoint les anciens. */
    public SessionBinding renamedTo(String renamed, Instant at) {
        List<String> previous = new ArrayList<>(MAX_FORMERS);
        previous.add(current);
        formers.stream().limit(MAX_FORMERS - 1L).forEach(previous::add);
        return new SessionBinding(renamed, previous, at);
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
