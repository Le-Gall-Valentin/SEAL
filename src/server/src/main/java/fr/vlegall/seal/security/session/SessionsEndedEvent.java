package fr.vlegall.seal.security.session;

import java.util.Set;

/**
 * TRN-12, AUTH-53, ADR 0043 (point 5) : des sessions viennent de finir (déconnexion, révocation). Les WebSockets qui
 * leur sont liées se ferment aussitôt ; une expiration, elle, est vue par la ronde de {@code ws}.
 */
public record SessionsEndedEvent(Set<String> sessionIds) {

    public SessionsEndedEvent {
        sessionIds = Set.copyOf(sessionIds);
    }
}
