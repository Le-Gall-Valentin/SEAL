package fr.vlegall.seal.support;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.application.port.in.InboundFrameHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;

/**
 * Gestionnaire de trame des tests de la WebSocket : {@code test.echo} rend sa charge dans l'{@code ack}, et compte
 * comme une action de l'utilisateur (AUTH-52, ADR 0043 point 4).
 */
@TestConfiguration(proxyBeanMethods = false)
public class WebSocketTestConfiguration {

    /** Type de la trame d'essai, née d'une action de l'utilisateur (AUTH-52), absent du protocole. */
    public static final String ECHO = "test.echo";

    /** Type de la trame d'essai sans action de l'utilisateur (comme {@code typing}), absent du protocole. */
    public static final String PEEK = "test.peek";

    @Bean
    InboundFrameHandler echoFrameHandler() {
        return echo(ECHO, true);
    }

    @Bean
    InboundFrameHandler peekFrameHandler() {
        return echo(PEEK, false);
    }

    private static InboundFrameHandler echo(String type, boolean userAction) {
        return new InboundFrameHandler() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public boolean userAction() {
                return userAction;
            }

            @Override
            public JsonNode handle(UserId user, JsonNode payload) {
                return payload;
            }
        };
    }
}
