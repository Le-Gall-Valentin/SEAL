package fr.vlegall.seal.authentication.application.port.in;

/** Demande de réinitialisation du mot de passe (AUTH-70, ADR 0022). */
public interface RequestPasswordResetUseCase {

    /**
     * Envoie un lien si le courriel est celui d'un compte actif qui a un mot de passe ; ne fait rien sinon. L'appelant
     * ne sait jamais lequel : la réponse est la même (AUTH-70, ADR 0022 point 3).
     */
    void request(String email);
}
