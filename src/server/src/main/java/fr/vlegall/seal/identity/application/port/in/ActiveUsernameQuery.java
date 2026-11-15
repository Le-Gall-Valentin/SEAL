package fr.vlegall.seal.identity.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/**
 * Identifiant d'un compte actif, demandé par le contexte {@code keys} pour le certificat d'identité (KEY-12, ADR 0008).
 * Moindre privilège : l'identifiant seul, ni courriel ni mots personnels. Appelé depuis un autre contexte, la signature
 * ne porte que des types de la JDK et {@link UserId}.
 */
public interface ActiveUsernameQuery {

    /** Identifiant définitif, sans {@code @}, du compte s'il est actif ; vide pour un compte en attente, supprimé ou inconnu. */
    Optional<String> activeUsername(UserId user);
}
