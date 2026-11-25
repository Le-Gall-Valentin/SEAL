package fr.vlegall.seal.security.session;

import fr.vlegall.seal.common.UserId;

/**
 * TRN-12, AUTH-23, ADR 0043 : une session a changé d'identifiant (connexion, vérification, ré-authentification), pour le
 * compte {@code user}. Les WebSockets du même compte suivent le nouvel identifiant ; celles d'un autre compte se
 * ferment.
 */
public record SessionRenewedEvent(String previousId, String currentId, UserId user) {}
