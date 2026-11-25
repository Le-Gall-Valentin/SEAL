package fr.vlegall.seal.ws.infrastructure.web;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** TRN-10 : point d'accès {@code /ws}, WebSocket brute en trames texte JSON, sans STOMP. */
@Component
public class SealWebSocketHandler extends TextWebSocketHandler {}
