package fr.vlegall.seal.identity.domain.model;

import java.util.List;

/**
 * Mots personnels d'AUTH-11, qui ne comptent pas dans la longueur d'un mot de passe : l'identifiant et la partie locale
 * du courriel, avant {@code +} et {@code @}, dans cet ordre (ADR 0019, point 2).
 */
public final class PersonalWords {

    private PersonalWords() {}

    public static List<String> of(Username username, EmailAddress email) {
        return List.of(username.value(), email.localPart());
    }
}
