package fr.vlegall.seal.support;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * Client WebSocket des tests (TRN-10), sur le client du JDK : il ouvre une vraie connexion vers le serveur démarré par
 * {@link SealServerTest}, avec le cookie d'une session ouverte par MockMvc et l'en-tête {@code Origin} voulu.
 */
public final class TestWebSocketClient {

    /** Origine configurée de SEAL (TRN-02) : celle que le navigateur envoie. */
    public static final String ORIGIN = "https://localhost:8443";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final int port;

    public TestWebSocketClient(int port) {
        this.port = port;
    }

    /** Statut de la poignée de main : 101 si la connexion s'ouvre, sinon celui de la réponse HTTP. */
    public int handshakeStatus(@Nullable Cookie session, @Nullable String origin) throws Exception {
        try {
            Connection connection = connect(session, origin);
            connection.socket().abort();
            return 101;
        } catch (ExecutionException | CompletionException failure) {
            if (failure.getCause() instanceof WebSocketHandshakeException refused) {
                return refused.getResponse().statusCode();
            }
            throw failure;
        }
    }

    /** Connexion ouverte ; les trames texte reçues et la fermeture sont gardées pour le test. */
    public Connection connect(@Nullable Cookie session, @Nullable String origin) throws Exception {
        Listener listener = new Listener();
        HttpClient client = HttpClient.newHttpClient();
        WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(TIMEOUT);
        if (session != null) {
            builder.header("Cookie", session.getName() + "=" + session.getValue());
        }
        if (origin != null) {
            builder.header("Origin", origin);
        }
        WebSocket socket = builder.buildAsync(URI.create("ws://localhost:" + port + "/ws"), listener)
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        return new Connection(socket, listener);
    }

    /** Une connexion de test : ce qu'elle reçoit, et comment elle se ferme. */
    public record Connection(WebSocket socket, Listener listener) {

        /** Envoie une trame texte entière. */
        public void send(String text) throws Exception {
            socket.sendText(text, true).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        /** Prochaine trame texte reçue, ou {@code null} après le délai. */
        public @Nullable String next(Duration within) throws InterruptedException {
            return listener.frames.poll(within.toMillis(), TimeUnit.MILLISECONDS);
        }

        /** Code de fermeture reçu du serveur. */
        public int closeCode(Duration within) throws Exception {
            return listener.closed.get(within.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Garde les trames texte complètes et le code de fermeture. */
    public static final class Listener implements WebSocket.Listener {

        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final CompletableFuture<Integer> closed = new CompletableFuture<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public @Nullable CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                frames.add(partial.toString());
                partial.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public @Nullable CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed.complete(statusCode);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closed.completeExceptionally(error);
        }
    }
}
