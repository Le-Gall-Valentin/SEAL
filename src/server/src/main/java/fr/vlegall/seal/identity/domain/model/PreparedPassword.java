package fr.vlegall.seal.identity.domain.model;

import java.util.Objects;

/**
 * Haché du premier mot de passe, préparé par le contexte {@code authentication} avant la transaction d'inscription
 * (ADR 0019, point 3). {@code identity} le transmet sans le lire ni le stocker ; {@link #toString()} ne le révèle pas
 * (SEC-21).
 */
public record PreparedPassword(String hash) {

    public PreparedPassword {
        Objects.requireNonNull(hash, "hash");
    }

    @Override
    public String toString() {
        return "PreparedPassword[***]";
    }
}
