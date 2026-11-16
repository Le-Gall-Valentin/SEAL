package fr.vlegall.seal.keys.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.Optional;

/** Compte titulaire d'un certificat, demandé au contexte {@code identity}. */
@FunctionalInterface
public interface AccountPort {

    /** Identifiant définitif du compte s'il est actif (KEY-12), vide sinon. */
    Optional<String> activeUsername(UserId user);
}
