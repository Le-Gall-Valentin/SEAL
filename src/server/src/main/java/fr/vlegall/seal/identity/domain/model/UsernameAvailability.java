package fr.vlegall.seal.identity.domain.model;

import java.util.List;

/**
 * Réponse d'AUTH-05.
 *
 * @param available faux si l'identifiant est invalide, réservé ou pris
 * @param suggestions trois identifiants libres et non réservés (ADR 0019, point 5)
 */
public record UsernameAvailability(boolean available, List<String> suggestions) {

    public UsernameAvailability {
        suggestions = List.copyOf(suggestions);
    }
}
