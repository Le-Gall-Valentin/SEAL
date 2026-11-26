package fr.vlegall.seal.ws.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealServerTest;
import fr.vlegall.seal.support.SilentWebSocket;
import fr.vlegall.seal.support.TestAccount;
import fr.vlegall.seal.support.TestRelations;
import fr.vlegall.seal.support.TestWebSocketClient;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

/** TRN-13, A10, ADR 0044 (décision 10) : un client qui ne lit plus n'atteint ni les autres ni les requêtes HTTP. */
@SealServerTest
class SlowReaderIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ConnectionRegistry registry;

    /** Inonde de ping jusqu'à ce que le serveur ne lise plus : ses réponses remplissent les tampons TCP. */
    private static void floodUntilStalled(SilentWebSocket socket) throws InterruptedException {
        AtomicLong written = new AtomicLong();
        Thread flooder = new Thread(() -> {
            try {
                for (long i = 0; i < 5_000_000; i++) {
                    socket.sendText("{\"type\":\"ping\",\"id\":\"p" + i + "\"}");
                    written.incrementAndGet();
                }
            } catch (Exception stopped) {
                // Connexion fermée par le serveur : l'inondation s'arrête.
            }
        });
        flooder.setDaemon(true);
        flooder.start();
        long last = -1;
        long stableSince = System.nanoTime();
        long start = System.nanoTime();
        while (System.nanoTime() - start < Duration.ofSeconds(60).toNanos()) {
            Thread.sleep(200);
            long now = written.get();
            if (now != last) {
                last = now;
                stableSince = System.nanoTime();
            } else if (System.nanoTime() - stableSince > Duration.ofSeconds(1).toNanos()) {
                return;
            }
        }
        throw new AssertionError("le serveur lit toujours");
    }

    @Test
    @DisplayName(
            "TRN-13, A10, ADR 0044 : un lecteur lent est fermé dès que sa file dépasse 512 Kio, sans retenir les autres membres")
    void trn13SlowReaderIsClosedWithoutHoldingOthers() throws Exception {
        TestAccount alice = TestAccount.fresh(mvc, mail, "rapide");
        TestAccount bob = TestAccount.fresh(mvc, mail, "lent");
        TestAccount carol = TestAccount.fresh(mvc, mail, "membre");
        UUID group = TestRelations.group(jdbc, alice.id, bob.id, carol.id);
        TestWebSocketClient.Connection carolSocket =
                new TestWebSocketClient(port).connect(carol.session, TestWebSocketClient.ORIGIN);

        try (SilentWebSocket stuck = new SilentWebSocket(port, bob.session)) {
            // Le serveur continue de lire Bob (l'écriture ne le bloque pas) : ses réponses s'entassent dans sa file.
            floodUntilStalled(stuck);
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (!registry.byUser(new UserId(bob.id)).isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertThat(registry.byUser(new UserId(bob.id)))
                    .as("le lecteur lent est fermé")
                    .isEmpty();

            MvcResult renamed = mvc.perform(
                            alice.unsafe(patch("/api/v1/conversations/" + group), Map.of("name", "Sans attente")))
                    .andReturn();

            assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
            String frame = carolSocket.next(Duration.ofSeconds(5));
            assertThat(frame).as("l'autre membre est prévenu").isNotNull();
            assertThat(JSON.readTree(frame).get("type").asString()).isEqualTo("conv.update");
        }
    }
}
