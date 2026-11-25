package fr.vlegall.seal.ws.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealServerTest;
import fr.vlegall.seal.support.TestAccount;
import fr.vlegall.seal.support.TestRelations;
import fr.vlegall.seal.support.TestWebSocketClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** FRD-04, FRD-11, GRP-03, CNV-04 : les changements d'amitié et de conversation arrivent en direct. */
@SealServerTest
class RealtimeEventsIT {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Duration QUIET = Duration.ofMillis(500);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private JdbcClient jdbc;

    private TestAccount alice;
    private TestAccount bob;

    @BeforeEach
    void openAccounts() throws Exception {
        alice = TestAccount.fresh(mvc, mail, "alice");
        bob = TestAccount.fresh(mvc, mail, "bob");
    }

    private TestWebSocketClient.Connection connect(TestAccount account) throws Exception {
        return new TestWebSocketClient(port).connect(account.session, TestWebSocketClient.ORIGIN);
    }

    private static JsonNode next(TestWebSocketClient.Connection connection, String type) throws Exception {
        String text = connection.next(WAIT);
        assertThat(text).as("trame " + type).isNotNull();
        JsonNode frame = JSON.readTree(text);
        assertThat(frame.get("type").asString()).isEqualTo(type);
        return frame.get("payload");
    }

    @Test
    @DisplayName("FRD-04 : une invitation arrive en direct à son seul destinataire")
    void frd04InvitationReachesTheReceiverOnly() throws Exception {
        TestWebSocketClient.Connection sender = connect(alice);
        TestWebSocketClient.Connection receiver = connect(bob);

        mvc.perform(alice.unsafe(post("/api/v1/friend-requests"), Map.of("username", bob.username)))
                .andExpect(status().isCreated());

        assertThat(next(receiver, "friend.request").get("requestId").asString()).isNotBlank();
        assertThat(sender.next(QUIET)).as("rien pour l'émetteur").isNull();
    }

    @Test
    @DisplayName("FRD-11 : un retrait d'ami est annoncé aux deux comptes, chacun avec l'identifiant de l'autre")
    void frd11RemovalIsAnnouncedToBoth() throws Exception {
        TestRelations.befriend(jdbc, alice.id, bob.id);
        TestWebSocketClient.Connection remover = connect(alice);
        TestWebSocketClient.Connection removed = connect(bob);

        mvc.perform(alice.unsafe(delete("/api/v1/friends/" + bob.id), null)).andExpect(status().isNoContent());

        assertThat(next(remover, "friend.update").get("userId").asString()).isEqualTo(bob.id.toString());
        assertThat(next(removed, "friend.update").get("userId").asString()).isEqualTo(alice.id.toString());
    }

    @Test
    @DisplayName("GRP-03, CNV-04 : un ajout au groupe est annoncé à l'arrivant et à chaque membre")
    void grp03AdditionIsAnnouncedToNewcomerAndMembers() throws Exception {
        TestAccount carol = TestAccount.fresh(mvc, mail, "carol");
        TestRelations.befriend(jdbc, alice.id, bob.id);
        TestRelations.befriend(jdbc, alice.id, carol.id);
        UUID group = TestRelations.group(jdbc, alice.id, bob.id);
        List<TestWebSocketClient.Connection> everyone = List.of(connect(alice), connect(bob), connect(carol));

        mvc.perform(alice.unsafe(
                        post("/api/v1/conversations/" + group + "/members"), Map.of("userIds", List.of(carol.id))))
                .andExpect(status().isOk());

        for (TestWebSocketClient.Connection connection : everyone) {
            assertThat(next(connection, "conv.update").get("conversationId").asString())
                    .isEqualTo(group.toString());
        }
    }

    @Test
    @DisplayName("CNV-04, ADR 0043 : une requête refusée n'annonce rien")
    void cnv04RefusedRequestAnnouncesNothing() throws Exception {
        UUID group = TestRelations.group(jdbc, alice.id, bob.id);
        TestWebSocketClient.Connection member = connect(bob);

        mvc.perform(alice.unsafe(
                        post("/api/v1/conversations/" + group + "/members"),
                        Map.of("userIds", List.of(bob.id, UUID.randomUUID()))))
                .andExpect(status().is4xxClientError());

        assertThat(member.next(QUIET)).isNull();
    }
}
