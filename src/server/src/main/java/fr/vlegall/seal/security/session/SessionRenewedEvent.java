package fr.vlegall.seal.security.session;

/**
 * TRN-12, AUTH-23, ADR 0044 : une session a changé d'identifiant (connexion, vérification, ré-authentification par mot
 * de passe ou par OAuth). Les WebSockets liées à l'ancien identifiant suivent le nouveau ; la ronde de {@code ws} juge
 * ensuite le compte que garde la session.
 */
public record SessionRenewedEvent(String previousId, String currentId) {}
