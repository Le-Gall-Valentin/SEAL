package fr.vlegall.seal.identity.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpdateProfileHandlerTest {

    private final InMemoryIdentity identity = new InMemoryIdentity();
    private final UpdateProfileHandler handler = new UpdateProfileHandler(identity.users());

    private static fr.vlegall.seal.identity.domain.model.DisplayName name(String raw) {
        return fr.vlegall.seal.identity.domain.model.DisplayName.parse(raw).orElseThrow();
    }

    @Test
    @DisplayName("ACC-03 : le nom affiché d'un compte actif est remplacé")
    void acc03ReplacesTheDisplayName() {
        Account account = identity.add("valentin", "v@exemple.ca", AccountStatus.ACTIVE);

        handler.changeDisplayName(account.id(), name("Valentin Le Gall"));

        assertThat(identity.accounts.get(account.id()).displayName()).isEqualTo(name("Valentin Le Gall"));
    }

    @Test
    @DisplayName("SEC-03 : un compte en attente ne change pas de nom, même si la règle d'URL laissait passer")
    void sec03PendingAccountIsRefused() {
        Account account = identity.add("attente", "a@exemple.ca", AccountStatus.PENDING_EMAIL);

        assertThatThrownBy(() -> handler.changeDisplayName(account.id(), name("Autre")))
                .isInstanceOfSatisfying(
                        SealException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(identity.accounts.get(account.id()).displayName()).isEqualTo(name("Nom"));
    }

    @Test
    @DisplayName("SEC-03 : un compte supprimé ou inconnu n'obtient rien (échec fermé)")
    void sec03UnknownOrDeletedAccountIsUnauthenticated() {
        Account deleted = identity.add("parti", "p@exemple.ca", AccountStatus.DELETED);

        for (UserId user : new UserId[] {deleted.id(), new UserId(UUID.randomUUID())}) {
            assertThatThrownBy(() -> handler.changeDisplayName(user, name("Autre")))
                    .isInstanceOfSatisfying(
                            SealException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        }
        assertThat(identity.accounts.get(deleted.id()).displayName()).isEqualTo(name("Nom"));
    }
}
