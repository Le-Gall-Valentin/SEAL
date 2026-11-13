package fr.vlegall.seal.authentication.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialVersionHandlerTest {

    private final InMemoryCredentials credentials = new InMemoryCredentials();
    private final CredentialVersionHandler handler = new CredentialVersionHandler(credentials);

    @Test
    @DisplayName("AUTH-70 : la version suit chaque remplacement du haché ; un compte sans mot de passe n'en a pas")
    void auth70VersionFollowsEachHashChange() {
        UserId user = new UserId(UUID.randomUUID());

        assertThat(handler.currentVersion(user)).isEqualTo(OptionalLong.empty());
        credentials.save(user, "hash(premier)");
        assertThat(handler.currentVersion(user)).isEqualTo(OptionalLong.of(1));
        credentials.changeHash(user, "hash(second)");
        assertThat(handler.currentVersion(user)).isEqualTo(OptionalLong.of(2));
    }
}
