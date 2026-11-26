package fr.vlegall.seal.ws.infrastructure.web;

import java.util.function.Consumer;
import org.springframework.web.socket.CloseStatus;

/** Écriture d'une WebSocket : asynchrone, une seule trame à la fois (TRN-13, ADR 0044, décision 10). */
interface FrameSocket {

    /** Écrit une trame sans attendre ; {@code done} reçoit vrai une fois écrite, faux si l'écriture a échoué. */
    void sendText(String text, Consumer<Boolean> done);

    /**
     * Ferme la connexion avec ce code. Tomcat écrit la trame de fermeture d'un code autre que 1000 en 50 ms au plus
     * ({@code ABNORMAL_SESSION_CLOSE_SEND_TIMEOUT}), puis coupe.
     */
    void close(CloseStatus status);
}
