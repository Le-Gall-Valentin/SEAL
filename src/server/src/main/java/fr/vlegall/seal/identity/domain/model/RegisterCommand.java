package fr.vlegall.seal.identity.domain.model;

import java.util.Objects;

/**
 * Inscription classique (AUTH-10). L'identifiant reste brut : son refus doit répondre {@code USERNAME_INVALID}, que
 * seul le domaine décide (ADR 0019, point 4). {@link #toString()} ne révèle ni le courriel ni le mot de passe.
 */
public record RegisterCommand(EmailAddress email, String username, DisplayName displayName, String password) {

    public RegisterCommand {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(password, "password");
    }

    @Override
    public String toString() {
        return "RegisterCommand[username=" + username + ", password=***]";
    }
}
