package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealServerTest;
import fr.vlegall.seal.support.TestAccount;
import fr.vlegall.seal.support.TestWebSocketClient;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** TRN-13, TRN-14 : format des trames, réponses par le même id, fermeture d'une trame illisible ou trop grande. */
@SealServerTest
class WebSocketFramesIT {

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
        account = TestAccount.fresh(mvc, mail, "trame");
    }

    private TestWebSocketClient.Connection connect() throws Exception {
        return new TestWebSocketClient(port).connect(account.session, TestWebSocketClient.ORIGIN);
    }

    private static JsonNode reply(TestWebSocketClient.Connection connection) throws Exception {
        String text = connection.next(WAIT);
        assertThat(text).as("réponse du serveur").isNotNull();
        return JSON.readTree(text);
    }

    @Test
    @DisplayName("TRN-14 : ping reçoit pong au même id")
    void trn14PingGetsPong() throws Exception {
        TestWebSocketClient.Connection connection = connect();

        connection.send("{\"type\":\"ping\",\"id\":\"p-1\",\"payload\":{}}");

        JsonNode pong = reply(connection);
        assertThat(pong.get("type").asString()).isEqualTo("pong");
        assertThat(pong.get("id").asString()).isEqualTo("p-1");
    }

    @Test
    @DisplayName("TRN-14 : un type inconnu reçoit error VALIDATION_FAILED au même id, et la connexion reste")
    void trn14UnknownTypeGetsAnError() throws Exception {
        TestWebSocketClient.Connection connection = connect();

        connection.send("{\"type\":\"inconnu\",\"id\":\"u-1\",\"payload\":{}}");

        JsonNode error = reply(connection);
        assertThat(error.get("type").asString()).isEqualTo("error");
        assertThat(error.get("id").asString()).isEqualTo("u-1");
        assertThat(error.get("payload").get("code").asString()).isEqualTo("VALIDATION_FAILED");
        connection.send("{\"type\":\"ping\",\"id\":\"p-2\"}");
        assertThat(reply(connection).get("type").asString()).isEqualTo("pong");
    }

    @Test
    @DisplayName("TRN-14 : une trame traitée par un gestionnaire reçoit ack au même id, avec sa réponse")
    void trn14HandledFrameGetsAck() throws Exception {
        TestWebSocketClient.Connection connection = connect();

        connection.send("{\"type\":\"test.echo\",\"id\":\"e-1\",\"payload\":{\"texte\":\"bonjour\"}}");

        JsonNode ack = reply(connection);
        assertThat(ack.get("type").asString()).isEqualTo("ack");
        assertThat(ack.get("id").asString()).isEqualTo("e-1");
        assertThat(ack.get("payload").get("texte").asString()).isEqualTo("bonjour");
    }

    @Test
    @DisplayName("TRN-14, A10 : un champ inconnu ferme la connexion en 1008")
    void trn14UnknownFieldCloses() throws Exception {
        TestWebSocketClient.Connection connection = connect();

        connection.send("{\"type\":\"ping\",\"id\":\"1\",\"payload\":{},\"extra\":1}");

        assertThat(connection.closeCode(WAIT)).isEqualTo(1008);
    }

    @Test
    @DisplayName("TRN-14, A10 : du texte qui n'est pas du JSON ferme la connexion en 1008")
    void trn14InvalidJsonCloses() throws Exception {
        TestWebSocketClient.Connection connection = connect();

        connection.send("pas du JSON");

        assertThat(connection.closeCode(WAIT)).isEqualTo(1008);
    }

    @Test
    @DisplayName("TRN-10 : une trame binaire ferme la connexion en 1008")
    void trn10BinaryFrameCloses() throws Exception {
        TestWebSocketClient.Connection connection = connect();

        connection
                .socket()
                .sendBinary(ByteBuffer.wrap(new byte[] {1, 2, 3}), true)
                .get(5, TimeUnit.SECONDS);

        assertThat(connection.closeCode(WAIT)).isEqualTo(1008);
    }

    @Test
    @DisplayName("TRN-13 : une trame de 64 Kio plus un octet ferme la connexion en 1009")
    void trn13OversizedFrameCloses() throws Exception {
        TestWebSocketClient.Connection connection = connect();
        String prefix = "{\"type\":\"test.echo\",\"id\":\"g\",\"payload\":{\"x\":\"";
        String suffix = "\"}}";
        String text = prefix + "a".repeat(65_537 - prefix.length() - suffix.length()) + suffix;

        connection.send(text);

        assertThat(connection.closeCode(WAIT)).isEqualTo(1009);
    }

    @Test
    @DisplayName("TRN-13 : la limite de 64 Kio se compte en octets UTF-8, pas en caractères")
    void trn13LimitCountsUtf8Bytes() throws Exception {
        TestWebSocketClient.Connection connection = connect();
        // 40 000 « é » : 40 000 caractères, mais 80 000 octets en UTF-8.
        String text = "{\"type\":\"test.echo\",\"id\":\"g\",\"payload\":{\"x\":\"" + "é".repeat(40_000) + "\"}}";

        connection.send(text);

        assertThat(connection.closeCode(WAIT)).isEqualTo(1009);
    }

    @Test
    @DisplayName("TRN-13 : une trame de 64 Kio exactement passe")
    void trn13FrameOfExactly64KiBPasses() throws Exception {
        TestWebSocketClient.Connection connection = connect();
        String prefix = "{\"type\":\"test.echo\",\"id\":\"g\",\"payload\":{\"x\":\"";
        String suffix = "\"}}";
        String text = prefix + "a".repeat(65_536 - prefix.length() - suffix.length()) + suffix;

        connection.send(text);

        assertThat(reply(connection).get("type").asString()).isEqualTo("ack");
    }

    @Test
    @DisplayName("TRN-14, MSG-27, ADR 0044 (décision 3) : un gestionnaire nomme son accusé ; ack reste le défaut")
    void trn14HandlerNamesItsReply() throws Exception {
        TestWebSocketClient.Connection connection = connect();

        connection.send("{\"type\":\"test.named\",\"id\":\"n1\",\"payload\":{\"v\":1}}");
        String reply = connection.next(WAIT);

        assertThat(reply).isEqualTo("{\"type\":\"test.done\",\"id\":\"n1\",\"payload\":{\"v\":1}}");
    }
}
