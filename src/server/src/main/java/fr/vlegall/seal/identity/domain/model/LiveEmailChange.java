package fr.vlegall.seal.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Code vivant d'un changement de courriel (ACC-02) et la nouvelle adresse qu'il confirme. */
public record LiveEmailChange(UUID id, byte[] hash, EmailAddress target) {

    public LiveEmailChange {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(target, "target");
        hash = Objects.requireNonNull(hash, "hash").clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }

    /** Comparaison en temps constant, par {@link VerificationCode#matches(byte[])}. */
    public boolean isMatchedBy(VerificationCode code) {
        return code.matches(hash);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LiveEmailChange change && id.equals(change.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** SEC-21 : ni le haché ni l'adresse n'apparaissent dans une trace. */
    @Override
    public String toString() {
        return "LiveEmailChange[id=" + id + "]";
    }
}
