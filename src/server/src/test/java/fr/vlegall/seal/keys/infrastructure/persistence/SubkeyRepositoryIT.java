package fr.vlegall.seal.keys.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/** KEY-05, ADR 0025 (point 15) : le verrou des sous-clés d'un compte ne vaut que dans une transaction. */
@SealIntegrationTest
class SubkeyRepositoryIT {

    @Autowired
    private SubkeyRepository subkeys;

    @Test
    @DisplayName("KEY-05, ADR 0025 (point 15) : hors transaction, le verrou est refusé au lieu de tomber aussitôt")
    void key05AccountLockNeedsATransaction() {
        UserId user = new UserId(UUID.randomUUID());

        assertThatThrownBy(() -> subkeys.lockAccount(user)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
