package fr.vlegall.seal.authentication.application.port.in;

import fr.vlegall.seal.authentication.domain.model.LoginOutcome;

/** Connexion par mot de passe (AUTH-20, AUTH-21), appelée par le fournisseur de l'{@code AuthenticationManager}. */
public interface LoginUseCase {

    /**
     * Vérifie le mot de passe du compte que désigne la saisie. Exactement une vérification Argon2id a lieu, réelle ou
     * leurre, pour un temps de réponse constant (AUTH-21). Refusé dans une transaction (ADR 0019 point 3, ADR 0020).
     */
    LoginOutcome login(String login, String password);
}
