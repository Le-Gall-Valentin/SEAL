package fr.vlegall.seal.ws.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.ws.application.FrameText;
import fr.vlegall.seal.ws.application.port.in.NotifyUsersUseCase;
import fr.vlegall.seal.ws.domain.model.ConnectionRegistry;
import fr.vlegall.seal.ws.domain.port.out.FrameDispatcher;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

@ApplicationService
public class NotifyUsersHandler implements NotifyUsersUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(NotifyUsersHandler.class);

    private final ConnectionRegistry registry;
    private final FrameDispatcher dispatcher;

    public NotifyUsersHandler(ConnectionRegistry registry, FrameDispatcher dispatcher) {
        this.registry = registry;
        this.dispatcher = dispatcher;
    }

    @Override
    public void announce(Set<UserId> users, String type, JsonNode payload) {
        Set<UserId> targets = Set.copyOf(users);
        JsonNode frozen = payload.deepCopy();
        // ADR 0044 (décision 10) : une tâche par connexion, hors du fil de la requête ; un échec reste isolé.
        Runnable send = () -> {
            String text = FrameText.of(type, UUID.randomUUID().toString(), frozen);
            targets.forEach(user -> registry.byUser(user)
                    .forEach(connection -> dispatcher.dispatch(() -> {
                        try {
                            connection.send(text);
                        } catch (RuntimeException failed) {
                            LOG.debug(
                                    "Annonce non remise à une connexion ({})",
                                    failed.getClass().getSimpleName());
                        }
                    })));
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
