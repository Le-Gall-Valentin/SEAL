package fr.vlegall.seal.security.session;

/** AUTH-23, ADR 0045 : lignes de session en base, que la migration supprime elle-même pour savoir si elle existait. */
@FunctionalInterface
public interface StoredSessions {

    /** Supprime la session de cet identifiant ; vrai si une ligne l'était encore. */
    boolean delete(String sessionId);
}
