package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.common.UserId;
import java.util.List;

/**
 * Premier mot de passe d'un compte, à l'inscription (AUTH-11, AUTH-13). Appelé depuis un autre contexte : la
 * signature ne porte que des types de la JDK et {@link UserId} (ADR 0008).
 */
public interface SetUpPasswordUseCase {

    /**
     * Vérifie le mot de passe, puis l'enregistre haché dans la transaction de l'appelant, obligatoire.
     *
     * @param personalWords identifiant et partie locale du courriel, qui ne comptent pas (ADR 0019)
     * @return vrai si le mot de passe est accepté et enregistré ; faux s'il enfreint AUTH-11, sans rien écrire
     */
    boolean setUp(UserId user, String password, List<String> personalWords);
}
