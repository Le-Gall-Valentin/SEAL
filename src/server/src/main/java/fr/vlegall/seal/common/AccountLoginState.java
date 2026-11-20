package fr.vlegall.seal.common;

/**
 * État d'un compte pour une connexion OAuth (AUTH-16, AUTH-31), que le contexte {@code identity} donne au contexte
 * {@code authentication} par son port d'entrée : un vocabulaire partagé, comme {@link UserId}.
 */
public enum AccountLoginState {
    /** Compte actif : session complète. */
    ACTIVE,
    /** Courriel à vérifier : session restreinte. */
    PENDING_EMAIL,
    /** Compte supprimé ou inconnu : aucune session. */
    UNUSABLE
}
