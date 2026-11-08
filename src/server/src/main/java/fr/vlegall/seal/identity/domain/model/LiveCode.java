package fr.vlegall.seal.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Code vivant d'un compte : non consommé, non expiré, moins de 5 essais (AUTH-14). */
public record LiveCode(UUID id, byte[] hash) {

    public LiveCode {
        Objects.requireNonNull(id, "id");
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
        return other instanceof LiveCode code && id.equals(code.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "LiveCode[id=" + id + "]";
    }
}
