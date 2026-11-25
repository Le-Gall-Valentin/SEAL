package fr.vlegall.seal.ws.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.application.FrameText;
import fr.vlegall.seal.ws.application.port.in.NotifyUsersUseCase;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

@ApplicationService
public class NotifyUsersHandler implements NotifyUsersUseCase {

    private final ConnectionRegistry registry;

    public NotifyUsersHandler(ConnectionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void announce(Set<UserId> users, String type, JsonNode payload) {
        Set<UserId> targets = Set.copyOf(users);
        JsonNode frozen = payload.deepCopy();
        Runnable send = () -> {
            String text = FrameText.of(type, UUID.randomUUID().toString(), frozen);
            targets.forEach(user -> registry.byUser(user).forEach(connection -> connection.send(text)));
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        // ADR 0043 : rien n'est annoncé avant la validation, ni après une annulation.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
}
