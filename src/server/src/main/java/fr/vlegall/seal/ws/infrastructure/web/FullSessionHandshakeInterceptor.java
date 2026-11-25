package fr.vlegall.seal.ws.infrastructure.web;

import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.FullSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * TRN-11 : la poignée de main de {@code /ws} exige l'en-tête {@code Origin} égal à l'origine configurée (TRN-02) et la
 * session complète d'un compte actif ; sinon 403, jamais 401. {@code setAllowedOrigins} laisse passer une requête sans
 * {@code Origin}, comme une requête de même origine : ce contrôle l'exige, parade au détournement de WebSocket
 * inter-sites (16.1). La connexion garde le compte et l'identifiant de la session HTTP (TRN-12).
 */
@Component
public class FullSessionHandshakeInterceptor implements HandshakeInterceptor {

    /** Attribut de la connexion : le compte de la session, {@link UserId}. */
    public static final String USER = "seal.userId";

    /** Attribut de la connexion : l'identifiant de la session HTTP à laquelle elle est liée (TRN-12). */
    public static final String SESSION = "seal.sessionId";

    private final String origin;

    public FullSessionHandshakeInterceptor(SealProperties properties) {
        this.origin = properties.origin().toString();
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {
        Optional<Binding> binding = origin.equals(request.getHeaders().getFirst(HttpHeaders.ORIGIN))
                        && request instanceof ServletServerHttpRequest servlet
                ? bindingOf(servlet.getServletRequest())
                : Optional.empty();
        if (binding.isEmpty()) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(USER, binding.get().user());
        attributes.put(SESSION, binding.get().sessionId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            @Nullable Exception exception) {}

    private static Optional<Binding> bindingOf(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return FullSessions.storedUser(request).map(user -> new Binding(user, session.getId()));
    }

    private record Binding(UserId user, String sessionId) {}
}
