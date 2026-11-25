package fr.vlegall.seal.ws.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import fr.vlegall.seal.ws.domain.model.FakeConnection;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class NotifyUsersHandlerTest {

    private static final UserId ALICE = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000001"));
    private static final UserId BOB = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000002"));
    private static final UserId CAROL = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000003"));
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final NotifyUsersHandler handler = new NotifyUsersHandler(registry);
    private final FakeConnection alicePhone = open(ALICE, "a1");
    private final FakeConnection aliceLaptop = open(ALICE, "a2");
    private final FakeConnection bob = open(BOB, "b1");
    private final JsonNode payload = JsonNodeFactory.instance.objectNode().put("requestId", "r1");

    private FakeConnection open(UserId user, String session) {
        FakeConnection connection = new FakeConnection(user, session, Instant.EPOCH);
        assertThat(registry.open(connection)).isTrue();
        return connection;
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName(
            "FRD-04, TRN-14 : hors transaction, la trame part aussitôt vers chaque connexion des seuls comptes visés")
    void frd04FrameReachesEveryConnectionOfTheTargetedAccounts() {
        handler.announce(Set.of(ALICE, CAROL), "friend.request", payload);

        assertThat(alicePhone.sent()).hasSize(1);
        assertThat(aliceLaptop.sent()).hasSize(1);
        assertThat(bob.sent()).isEmpty();
        JsonNode frame = JSON.readTree(alicePhone.sent().getFirst());
        assertThat(frame.get("type").asString()).isEqualTo("friend.request");
        assertThat(frame.get("id").asString()).matches("[A-Za-z0-9_-]{1,64}");
        assertThat(frame.get("payload")).isEqualTo(payload);
    }

    @Test
    @DisplayName("CNV-04, ADR 0043 : dans une transaction, la trame attend sa validation")
    void cnv04FrameWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        handler.announce(Set.of(BOB), "conv.update", payload);
        assertThat(bob.sent()).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(bob.sent()).hasSize(1);
    }

    @Test
    @DisplayName("A10, ADR 0043 : une transaction annulée n'annonce rien")
    void a10RolledBackTransactionAnnouncesNothing() {
        TransactionSynchronizationManager.initSynchronization();

        handler.announce(Set.of(BOB), "conv.update", payload);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(bob.sent()).isEmpty();
    }
}
