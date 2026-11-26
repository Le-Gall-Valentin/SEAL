package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.domain.model.CloseReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import tools.jackson.databind.json.JsonMapper;

class WebSocketConnectionTest {

    /** Socket dont le test règle chaque écriture : un client lent ne la règle jamais. */
    private static final class ManualSocket implements FrameSocket {
        final List<String> written = new ArrayList<>();
        final List<Consumer<Boolean>> pending = new ArrayList<>();

        @Nullable
        CloseStatus closed;

        int maxInFlight;
        boolean failing;

        @Override
        public void sendText(String text, Consumer<Boolean> done) {
            if (failing) {
                throw new IllegalStateException("socket fermée");
            }
            written.add(text);
            pending.add(done);
            maxInFlight = Math.max(maxInFlight, pending.size());
        }

        @Override
        public void close(CloseStatus status) {
            closed = status;
        }

        void complete(boolean ok) {
            pending.removeFirst().accept(ok);
        }

        void completeAll() {
            while (!pending.isEmpty()) {
                complete(true);
            }
        }
    }

    private final ManualSocket socket = new ManualSocket();
    private final WebSocketConnection connection = new WebSocketConnection(
            socket,
            new UserId(UUID.randomUUID()),
            "session",
            () -> {},
            new WireFrames(JsonMapper.builder().build()),
            Instant.EPOCH,
            Runnable::run);

    @Test
    @DisplayName("TRN-13, ADR 0044 : une seule écriture à la fois, les suivantes attendent leur tour, dans l'ordre")
    void trn13OneWriteAtATimeInOrder() {
        connection.send("un");
        connection.send("deux");
        connection.send("trois");

        assertThat(socket.written).containsExactly("un");
        socket.completeAll();
        socket.completeAll();
        socket.completeAll();
        assertThat(socket.written).containsExactly("un", "deux", "trois");
        assertThat(socket.maxInFlight).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "TRN-13, ADR 0044 (décision 10) : un client qui ne lit plus ne bloque pas l'appelant ; au-delà de 512 Kio, 1011")
    void trn13SlowClientIsClosedWithoutBlocking() {
        String frame = "x".repeat(60 * 1024);

        for (int i = 0; i < 10; i++) {
            connection.send(frame);
        }

        assertThat(socket.written).hasSize(1);
        assertThat(socket.closed).isEqualTo(CloseStatus.SERVER_ERROR);
    }

    @Test
    @DisplayName("TRN-13, ADR 0044 (décision 10) : une écriture refusée ou impossible ferme en 1011, sans remonter")
    void trn13FailedWriteClosesWith1011() {
        connection.send("un");
        socket.complete(false);
        assertThat(socket.closed).isEqualTo(CloseStatus.SERVER_ERROR);

        ManualSocket broken = new ManualSocket();
        broken.failing = true;
        WebSocketConnection other = new WebSocketConnection(
                broken,
                new UserId(UUID.randomUUID()),
                "s",
                () -> {},
                new WireFrames(JsonMapper.builder().build()),
                Instant.EPOCH,
                Runnable::run);
        assertThatNoException().isThrownBy(() -> other.send("deux"));
        assertThat(broken.closed).isEqualTo(CloseStatus.SERVER_ERROR);
    }

    @Test
    @DisplayName("TRN-12 : session.revoked part avant 4401, et la fermeture attend que la trame soit écrite")
    void trn12RevokedFrameThenClose() {
        connection.send("réponse en cours");
        connection.close(CloseReason.SESSION_ENDED);

        assertThat(socket.closed).isNull();
        socket.complete(true);
        assertThat(socket.written.getLast()).contains("\"session.revoked\"");
        assertThat(socket.closed).isNull();
        socket.complete(true);
        assertThat(socket.closed).isEqualTo(new CloseStatus(4401));
    }

    @Test
    @DisplayName("TRN-12, AUTH-53 : une fois la fermeture demandée, plus aucune trame ne part")
    void trn12NothingIsSentOnceClosing() {
        connection.close(CloseReason.SILENT);
        connection.send("après");

        assertThat(socket.written).isEmpty();
        assertThat(socket.closed).isEqualTo(new CloseStatus(4408));
    }
}
