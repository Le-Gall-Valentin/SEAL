package fr.vlegall.seal.authentication.domain.model;

import java.util.Objects;

/**
 * Mot de passe enregistré d'un compte : son haché et sa version, lus ensemble (ADR 0023). La version change à chaque
 * remplacement du haché.
 */
public record StoredCredential(String hash, long version) {

    public StoredCredential {
        Objects.requireNonNull(hash, "hash");
    }

    /** SEC-21 : le haché n'apparaît dans aucune trace. */
    @Override
    public String toString() {
        return "StoredCredential[hash=***, version=" + version + "]";
    }
}
