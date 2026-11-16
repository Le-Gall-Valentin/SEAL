package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-12, ADR 0008 : l'identifiant d'un compte actif, seul champ que le contexte keys reçoit d'identity. */
@DisplayName("KEY-12 : identifiant d'un compte actif")
class ActiveUsernameHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final ActiveUsernameHandler handler = new ActiveUsernameHandler(identity.users());

    @Test
    @DisplayName("KEY-12 : un compte actif donne son identifiant, sans @")
    void key12ActiveAccountGivesItsUsername() {
        Account active = identity.add("valentin", "valentin@exemple.ca", AccountStatus.ACTIVE);

        assertThat(handler.activeUsername(active.id())).contains("valentin");
    }

    @Test
    @DisplayName("KEY-12 : un compte en attente, supprimé ou inconnu ne donne rien")
    void key12InactiveOrUnknownAccountGivesNothing() {
        Account pending = identity.add("attente", "attente@exemple.ca", AccountStatus.PENDING_EMAIL);
        Account deleted = identity.add("supprime", "supprime@exemple.ca", AccountStatus.DELETED);

        assertThat(handler.activeUsername(pending.id())).isEmpty();
        assertThat(handler.activeUsername(deleted.id())).isEmpty();
        assertThat(handler.activeUsername(new UserId(UUID.randomUUID()))).isEmpty();
    }
}
