package fr.vlegall.seal.ws.infrastructure.config;

import fr.vlegall.seal.common.SealProperties;
import fr.vlegall.seal.ws.infrastructure.web.FullSessionHandshakeInterceptor;
import fr.vlegall.seal.ws.infrastructure.web.SealWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** TRN-10, TRN-11 : {@code /ws}, ouvert à la seule origine configurée (TRN-02), après l'intercepteur de session. */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    /** Chemin de la WebSocket (TRN-10), relayé par Vite en développement (TRN-23). */
    public static final String PATH = "/ws";

    private final SealProperties properties;
    private final SealWebSocketHandler handler;
    private final FullSessionHandshakeInterceptor interceptor;

    public WebSocketConfiguration(
            SealProperties properties, SealWebSocketHandler handler, FullSessionHandshakeInterceptor interceptor) {
        this.properties = properties;
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH)
                .addInterceptors(interceptor)
                .setAllowedOrigins(properties.origin().toString());
    }
}
