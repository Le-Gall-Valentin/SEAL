package fr.vlegall.seal.authentication.domain.port.out;

import fr.vlegall.seal.authentication.domain.model.ResetToken;

/** Courriel du lien de réinitialisation, envoyé après la validation de la transaction (AUTH-70, ADR 0019 point 8). */
@FunctionalInterface
public interface PasswordResetMailPort {

    void sendLink(String email, String username, ResetToken token);
}
