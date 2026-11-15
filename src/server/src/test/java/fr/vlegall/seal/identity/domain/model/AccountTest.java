package fr.vlegall.seal.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AUTH-16, KEY-12 : un compte n'est actif, donc vérifié et certifiable, qu'au statut ACTIVE. */
@DisplayName("AUTH-16 : compte actif")
class AccountTest {

    private static Account account(AccountStatus status) {
        return new Account(
                new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c")),
                Username.parse("valentin"),
                fr.vlegall.seal.identity.domain.model.DisplayName.parse("Valentin")
                        .orElseThrow(),
                status == AccountStatus.DELETED
                        ? null
                        : EmailAddress.parse("valentin@exemple.ca").orElseThrow(),
                status);
    }

    @Test
    @DisplayName("AUTH-16 : seul le statut ACTIVE fait un compte actif")
    void auth16OnlyActiveStatusIsActive() {
        assertThat(account(AccountStatus.ACTIVE).isActive()).isTrue();
        assertThat(account(AccountStatus.PENDING_EMAIL).isActive()).isFalse();
        assertThat(account(AccountStatus.DELETED).isActive()).isFalse();
    }
}
