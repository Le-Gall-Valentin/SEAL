package fr.vlegall.seal.authentication.infrastructure.web.dto;

import fr.vlegall.seal.authentication.domain.model.ResetView;
import java.time.Instant;

/** Réponse de {@code inspect} : ce que l'écran Nouveau-Mot-de-passe affiche (ADR 0022, point 1). */
public record PasswordResetView(String username, String email, Instant expiresAt) {

    public static PasswordResetView of(ResetView view) {
        return new PasswordResetView(view.username(), view.email(), view.expiresAt());
    }

    /** SEC-21 : le courriel n'apparaît dans aucune trace. */
    @Override
    public String toString() {
        return "PasswordResetView[username=" + username + ", email=***, expiresAt=" + expiresAt + "]";
    }
}
