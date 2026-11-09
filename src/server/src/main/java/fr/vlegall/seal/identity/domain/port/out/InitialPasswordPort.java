package fr.vlegall.seal.identity.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.PreparedPassword;
import java.util.List;
import java.util.Optional;

/** Premier mot de passe du compte, confié au contexte {@code authentication} (ADR 0008, ADR 0019 point 3). */
public interface InitialPasswordPort {

    /**
     * Vérifie le mot de passe (AUTH-11) puis le hache, hors de toute transaction.
     *
     * @param personalWords identifiant et partie locale du courriel (ADR 0019, point 2)
     * @return vide si le mot de passe enfreint AUTH-11
     */
    Optional<PreparedPassword> prepare(String password, List<String> personalWords);

    /** Enregistre le haché préparé dans la transaction en cours, obligatoire. */
    void store(UserId user, PreparedPassword password);
}
