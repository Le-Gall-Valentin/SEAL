package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import java.util.List;

/** Premier mot de passe du compte, confié au contexte {@code authentication} (ADR 0008). */
public interface InitialPasswordPort {

    /**
     * Vérifie puis enregistre le mot de passe dans la transaction en cours.
     *
     * @param personalWords identifiant et partie locale du courriel (ADR 0019, point 2)
     * @return faux si le mot de passe enfreint AUTH-11 ; rien n'est alors écrit
     */
    boolean establish(UserId user, String password, List<String> personalWords);
}
