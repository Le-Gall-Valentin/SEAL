package fr.vlegall.seal.common;

/**
 * État d'un compte vu du contexte {@code authentication} (AUTH-16, AUTH-31, SEC-03) : connexion OAuth,
 * ré-authentification, méthodes de connexion. Le contexte {@code identity} le donne par son port d'entrée : un
 * vocabulaire partagé, comme {@link UserId}.
 */
public enum AccountLoginState {
    /** Compte actif : session complète. */
    ACTIVE,
    /** Courriel à vérifier : session restreinte. */
    PENDING_EMAIL,
    /** Compte supprimé ou inconnu : aucune session. */
    UNUSABLE
}
