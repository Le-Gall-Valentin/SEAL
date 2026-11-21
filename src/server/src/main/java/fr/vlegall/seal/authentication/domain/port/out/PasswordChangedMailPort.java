package fr.vlegall.seal.authentication.domain.port.out;

/** ACC-01 : courriel qui prévient le titulaire d'un changement de mot de passe, envoyé après la validation. */
@FunctionalInterface
public interface PasswordChangedMailPort {

    void sendPasswordChanged(String email, String username);
}
