package fr.vlegall.seal.authentication.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Ce que l'écran Nouveau-Mot-de-passe montre d'un lien valable (ADR 0022, point 1). */
public record ResetView(String username, String email, Instant expiresAt) {

    public ResetView {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** SEC-21 : le courriel n'apparaît dans aucune trace. */
    @Override
    public String toString() {
        return "ResetView[username=" + username + ", email=***, expiresAt=" + expiresAt + "]";
    }
}
