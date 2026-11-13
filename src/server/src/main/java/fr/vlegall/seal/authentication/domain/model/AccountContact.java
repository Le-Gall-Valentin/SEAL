package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.UserId;
import java.util.List;
import java.util.Objects;

/**
 * Coordonnées d'un compte actif, données par {@code identity} (ADR 0008, ADR 0022) : de quoi lui écrire et appliquer
 * AUTH-11 à son nouveau mot de passe.
 *
 * @param personalWords identifiant et partie locale du courriel, qui ne comptent pas (AUTH-11)
 */
public record AccountContact(UserId user, String username, String email, List<String> personalWords) {

    public AccountContact {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(email, "email");
        personalWords = List.copyOf(personalWords);
    }
}
