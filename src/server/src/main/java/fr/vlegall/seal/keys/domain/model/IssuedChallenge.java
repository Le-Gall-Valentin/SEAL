package fr.vlegall.seal.keys.domain.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Défi remis au client, une seule fois : sa valeur ne quitte le serveur que dans cette réponse (KEY-03). */
public record IssuedChallenge(byte[] value, Instant expiresAt) {

    public IssuedChallenge {
        value = Objects.requireNonNull(value, "value").clone();
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public byte[] value() {
        return value.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IssuedChallenge that
                && Arrays.equals(value, that.value)
                && expiresAt.equals(that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    /** SEC-21 : le défi n'apparaît dans aucune trace. */
    @Override
    public String toString() {
        return "IssuedChallenge[***, expiresAt=" + expiresAt + "]";
    }
}
