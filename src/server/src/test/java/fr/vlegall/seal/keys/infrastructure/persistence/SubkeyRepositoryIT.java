package fr.vlegall.seal.keys.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.PublishedSubkey;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;

/** KEY-05, KEY-33, ADR 0025 (point 15) : sous-clés d'un compte, leur verrou et leur lecture. */
@SealIntegrationTest
class SubkeyRepositoryIT {

    private static final Instant EXPIRES_AT = Instant.parse("2026-12-28T12:00:00.123Z");

    @Autowired
    private SubkeyRepository subkeys;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void resetMail() {
        mail.reset();
    }

    @Test
    @DisplayName("KEY-05, ADR 0025 (point 15) : hors transaction, le verrou est refusé au lieu de tomber aussitôt")
    void key05AccountLockNeedsATransaction() {
        UserId user = new UserId(UUID.randomUUID());

        assertThatThrownBy(() -> subkeys.lockAccount(user)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("KEY-33, KEY-20 : une sous-clé est vivante jusqu'à son expiration exclue")
    void key33SubkeyLivesUntilItsExpiryExcluded() throws Exception {
        UserId user = new UserId(new AuthClient(mvc, mail)
                .register("sous_cle_borne@exemple.ca", "sous_cle_borne")
                .id());
        byte[] serial = new byte[16];
        serial[0] = 0x51;
        byte[] ekId = new byte[16];
        ekId[0] = 0x52;
        Instant createdAt = EXPIRES_AT.minus(SubkeyBinding.VALIDITY);
        jdbc.sql("INSERT INTO identity_certificates"
                        + " (serial, user_id, ik_public, not_before, not_after, cert_bytes, signature)"
                        + " VALUES (:serial, :user, :ik, :from, :to, :bytes, :signature)")
                .param("serial", serial)
                .param("user", user.value())
                .param("ik", new byte[32])
                .param("from", Timestamp.from(createdAt))
                .param("to", Timestamp.from(createdAt.plus(Duration.ofDays(397))))
                .param("bytes", new byte[1])
                .param("signature", new byte[64])
                .update();
        jdbc.sql("INSERT INTO subkeys (ek_id, user_id, cert_serial, kem, ek_public, created_at, expires_at,"
                        + " binding_signature) VALUES (:ekId, :user, :serial, :kem, :ek, :from, :to, :signature)")
                .param("ekId", ekId)
                .param("user", user.value())
                .param("serial", serial)
                .param("kem", SubkeyBinding.KEM)
                .param("ek", new byte[32])
                .param("from", Timestamp.from(createdAt))
                .param("to", Timestamp.from(EXPIRES_AT))
                .param("signature", new byte[64])
                .update();

        assertThat(subkeys.findLive(user, serial, EXPIRES_AT.minusMillis(1)))
                .extracting(PublishedSubkey::binding)
                .extracting(SubkeyBinding::expiresAt)
                .containsExactly(EXPIRES_AT);
        assertThat(subkeys.findLive(user, serial, EXPIRES_AT)).isEmpty();
    }
}
