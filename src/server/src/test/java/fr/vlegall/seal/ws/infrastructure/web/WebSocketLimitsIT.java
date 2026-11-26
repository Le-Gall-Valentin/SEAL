package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealServerTest;
import fr.vlegall.seal.support.TestAccount;
import fr.vlegall.seal.support.TestWebSocketClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TRN-13 : 5 connexions par utilisateur, 30 trames par 10 secondes et par connexion. */
@SealServerTest
class WebSocketLimitsIT {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    private TestAccount account;

    @BeforeEach
    void openAccount() throws Exception {
        account = TestAccount.fresh(mvc, mail, "limite");
    }

    private TestWebSocketClient.Connection connect() throws Exception {
        return new TestWebSocketClient(port).connect(account.session, TestWebSocketClient.ORIGIN);
    }

    private static JsonNode ping(TestWebSocketClient.Connection connection, String id) throws Exception {
        connection.send("{\"type\":\"ping\",\"id\":\"" + id + "\"}");
        String reply = connection.next(WAIT);
        assertThat(reply).as("réponse à " + id).isNotNull();
        return JSON.readTree(reply);
    }

    @Test
    @DisplayName("TRN-13 : la sixième connexion d'un compte est fermée en 4429, les cinq premières restent")
    void trn13SixthConnectionIsClosed() throws Exception {
        List<TestWebSocketClient.Connection> open = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            open.add(connect());
        }

        TestWebSocketClient.Connection sixth = connect();

        assertThat(sixth.closeCode(WAIT)).isEqualTo(4429);
        for (int i = 0; i < 5; i++) {
            assertThat(ping(open.get(i), "p" + i).get("type").asString()).isEqualTo("pong");
        }
    }

    @Test
    @DisplayName("TRN-13 : une connexion fermée libère sa place")
    void trn13ClosingFreesASlot() throws Exception {
        List<TestWebSocketClient.Connection> open = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            open.add(connect());
        }
        open.getFirst().socket().sendClose(1000, "").get();
        open.getFirst().closeCode(WAIT);

        TestWebSocketClient.Connection replacement = connect();

        assertThat(ping(replacement, "r").get("type").asString()).isEqualTo("pong");
    }

    @Test
    @DisplayName("TRN-13 : la 31e trame en 10 secondes reçoit error RATE_LIMITED avec retryAfter, la connexion reste")
    void trn13ThirtyFirstFrameIsRateLimited() throws Exception {
        TestWebSocketClient.Connection connection = connect();
        for (int i = 0; i < 30; i++) {
            assertThat(ping(connection, "p" + i).get("type").asString()).isEqualTo("pong");
        }

        JsonNode refused = ping(connection, "p30");

        assertThat(refused.get("type").asString()).isEqualTo("error");
        assertThat(refused.get("id").asString()).isEqualTo("p30");
        assertThat(refused.get("payload").get("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(refused.get("payload").get("retryAfter").asInt()).isBetween(1, 10);
        assertThatThrownBy(() -> connection.closeCode(Duration.ofSeconds(1)))
                .as("la connexion n'est pas fermée")
                .isInstanceOf(TimeoutException.class);
    }
}
