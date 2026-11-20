package fr.vlegall.seal.keys.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** KEY-33, QA-05 : un nouvel appareil relit son certificat et ses sous-clés valides (ADR 0029, point 4). */
@SealIntegrationTest
class UserKeysIT {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AuthoritySigner authority;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void reset() {
        mail.reset();
        clock.reset();
    }

    private static String keysOf(UUID user) {
        return "/api/v1/users/" + user + "/keys";
    }

    private ResultActions read(KeysTestClient caller, UUID owner) throws Exception {
        return mvc.perform(caller.client.from(get(keysOf(owner))).cookie(caller.session));
    }

    /** Publie une sous-clé créée maintenant, signée par l'IK du compte, et rend son ekId. */
    private byte[] publishSubkey(KeysTestClient account, byte[] serial) throws Exception {
        byte[] ekId = new byte[SubkeyBinding.EK_ID_BYTES];
        RANDOM.nextBytes(ekId);
        byte[] ekPublic = new byte[SubkeyBinding.EK_PUBLIC_BYTES];
        RANDOM.nextBytes(ekPublic);
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = createdAt.plus(SubkeyBinding.VALIDITY);
        SubkeyBinding binding = new SubkeyBinding(
                new UserId(account.id), serial, ekId, SubkeyBinding.KEM, ekPublic, createdAt, expiresAt);
        Map<String, Object> body = new HashMap<>();
        body.put("certSerial", Base64Url.encode(serial));
        body.put("ekId", Base64Url.encode(ekId));
        body.put("kem", SubkeyBinding.KEM);
        body.put("ekPublic", Base64Url.encode(ekPublic));
        body.put("createdAt", createdAt.toString());
        body.put("expiresAt", expiresAt.toString());
        body.put("signature", Base64Url.encode(Ed25519.sign(account.ik.privateKey(), binding.encode())));
        mvc.perform(account.unsafe(post("/api/v1/keys/subkeys"), body)).andExpect(status().isNoContent());
        return ekId;
    }

    private static byte[] bytes(JsonNode node, String field) {
        return Base64Url.decode(node.get(field).asString());
    }

    private static Instant instant(JsonNode node, String field) {
        return Instant.parse(node.get(field).asString());
    }

    @Test
    @DisplayName(
            "KEY-33 : l'appelant relit son certificat entier, que la CA signe, et ses sous-clés, plus récentes d'abord")
    void key33CallerReadsHisCertificateAndLiveSubkeys() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "cles_relues");
        byte[] serial = account.publishIdentity();
        byte[] older = publishSubkey(account, serial);
        clock.advance(Duration.ofMinutes(1));
        byte[] newer = publishSubkey(account, serial);

        JsonNode body = AuthClient.body(read(account, account.id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificate.username").value("cles_relues"))
                .andExpect(jsonPath("$.certificate.subjectId").value(account.id.toString()))
                .andExpect(jsonPath("$.certificate.usage").value(SealCertificate.USAGE))
                .andReturn());

        JsonNode certificate = body.get("certificate");
        SealCertificate rebuilt = new SealCertificate(
                bytes(certificate, "serial"),
                bytes(certificate, "issuerKeyId"),
                new UserId(account.id),
                certificate.get("username").asString(),
                bytes(certificate, "ikPublic"),
                instant(certificate, "notBefore"),
                instant(certificate, "notAfter"),
                certificate.get("usage").intValue());
        assertThat(rebuilt.serial()).isEqualTo(serial);
        assertThat(rebuilt.ikPublic()).isEqualTo(account.ik.publicKey());
        assertThat(Ed25519.verify(authority.key().publicKey(), rebuilt.encode(), bytes(certificate, "signature")))
                .isTrue();

        List<JsonNode> subkeys = body.get("subkeys").valueStream().toList();
        assertThat(subkeys).extracting(subkey -> bytes(subkey, "ekId")).containsExactly(newer, older);
        for (JsonNode subkey : subkeys) {
            SubkeyBinding binding = new SubkeyBinding(
                    new UserId(account.id),
                    serial,
                    bytes(subkey, "ekId"),
                    subkey.get("kem").asString(),
                    bytes(subkey, "ekPublic"),
                    instant(subkey, "createdAt"),
                    instant(subkey, "expiresAt"));
            assertThat(binding.isSignedBy(account.ik.publicKey(), bytes(subkey, "signature")))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("KEY-33 : une sous-clé expirée ou liée à un autre certificat n'est pas servie")
    void key33ExpiredAndForeignSubkeysAreLeftOut() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "cles_tri");
        byte[] serial = account.publishIdentity();
        byte[] live = publishSubkey(account, serial);
        byte[] revoked = new byte[16];
        RANDOM.nextBytes(revoked);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        jdbc.sql("INSERT INTO identity_certificates"
                        + " (serial, user_id, ik_public, not_before, not_after, cert_bytes, signature, revoked_at,"
                        + " revocation_reason) VALUES (:serial, :user, :ik, :from, :to, :bytes, :signature, :from, 4)")
                .param("serial", revoked)
                .param("user", account.id)
                .param("ik", new byte[32])
                .param("from", Timestamp.from(now.minus(Duration.ofDays(1))))
                .param("to", Timestamp.from(now.plus(Duration.ofDays(396))))
                .param("bytes", new byte[1])
                .param("signature", new byte[64])
                .update();
        jdbc.sql("INSERT INTO subkeys (ek_id, user_id, cert_serial, kem, ek_public, created_at, expires_at,"
                        + " binding_signature) VALUES (:ekId, :user, :serial, :kem, :ek, :from, :to, :signature)")
                .param("ekId", revoked)
                .param("user", account.id)
                .param("serial", revoked)
                .param("kem", SubkeyBinding.KEM)
                .param("ek", new byte[32])
                .param("from", Timestamp.from(now))
                .param("to", Timestamp.from(now.plus(SubkeyBinding.VALIDITY)))
                .param("signature", new byte[64])
                .update();
        // Sous-clé du certificat courant, expirée, écrite en base : avancer l'horloge de 45 jours expirerait la
        // session.
        byte[] expired = new byte[16];
        RANDOM.nextBytes(expired);
        jdbc.sql("INSERT INTO subkeys (ek_id, user_id, cert_serial, kem, ek_public, created_at, expires_at,"
                        + " binding_signature) VALUES (:ekId, :user, :serial, :kem, :ek, :from, :to, :signature)")
                .param("ekId", expired)
                .param("user", account.id)
                .param("serial", serial)
                .param("kem", SubkeyBinding.KEM)
                .param("ek", new byte[32])
                .param("from", Timestamp.from(now.minus(SubkeyBinding.VALIDITY).minusMillis(1)))
                .param("to", Timestamp.from(now.minusMillis(1)))
                .param("signature", new byte[64])
                .update();

        read(account, account.id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certificate.serial").value(Base64Url.encode(serial)))
                .andExpect(jsonPath("$.subkeys.length()").value(1))
                .andExpect(jsonPath("$.subkeys[0].ekId").value(Base64Url.encode(live)));
    }

    @Test
    @DisplayName("KEY-33 : sans certificat, ou avec un certificat expiré ou révoqué, 404 KEY_NOT_FOUND")
    void key33NoCurrentCertificateIsNotFound() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "cles_aucune");

        read(account, account.id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));

        byte[] serial = account.publishIdentity();
        read(account, account.id).andExpect(status().isOk());
        // Expiré puis révoqué, en base : avancer l'horloge de 397 jours expirerait la session.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        jdbc.sql("UPDATE identity_certificates SET not_before = :from, not_after = :to WHERE serial = :serial")
                .param(
                        "from",
                        Timestamp.from(now.minus(SealCertificate.VALIDITY).minusMillis(1)))
                .param("to", Timestamp.from(now.minusMillis(1)))
                .param("serial", serial)
                .update();
        read(account, account.id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));
        jdbc.sql("UPDATE identity_certificates SET not_before = :from, not_after = :to, revoked_at = :from,"
                        + " revocation_reason = 4 WHERE serial = :serial")
                .param("from", Timestamp.from(now))
                .param("to", Timestamp.from(now.plus(SealCertificate.VALIDITY)))
                .param("serial", serial)
                .update();
        read(account, account.id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));
    }

    @Test
    @DisplayName("A01 : les clés d'un autre compte, même certifié, répondent 404 KEY_NOT_FOUND (amis au lot 3)")
    void a01OtherAccountsKeysAreNotFound() throws Exception {
        KeysTestClient reader = KeysTestClient.account(mvc, mail, "cles_lecteur");
        KeysTestClient owner = KeysTestClient.account(mvc, mail, "cles_proprio");
        owner.publishIdentity();

        read(reader, owner.id)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));
        read(reader, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));
    }

    @Test
    @DisplayName("SEC-01 : un identifiant de compte qui n'est pas un UUID donne 400 VALIDATION_FAILED")
    void sec01MalformedUserIdIsRefused() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "cles_mal_formees");

        mvc.perform(account.client.from(get("/api/v1/users/pas-un-uuid/keys")).cookie(account.session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("QA-05 : sans session 401, en session restreinte 403")
    void qa05AccessIsChecked() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered pending = client.register("cles_restreint@exemple.ca", "cles_restreint");
        Cookie restricted = pending.session();

        mvc.perform(client.from(get(keysOf(pending.id()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.from(get(keysOf(pending.id()))).cookie(restricted))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
