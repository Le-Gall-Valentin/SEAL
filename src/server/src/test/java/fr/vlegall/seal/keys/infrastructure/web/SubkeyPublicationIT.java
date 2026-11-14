package fr.vlegall.seal.keys.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** KEY-05, KEY-21, SEC-20, QA-05 : publication d'une sous-clé, liée au certificat courant (ADR 0025, point 7). */
@SealIntegrationTest
class SubkeyPublicationIT {

    private static final String SUBKEYS = "/api/v1/keys/subkeys";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcClient jdbc;

    private int nextEkId;

    @AfterEach
    void reset() {
        mail.reset();
        clock.reset();
    }

    private Map<String, Object> binding(
            KeysTestClient account,
            byte[] serial,
            byte[] ekId,
            Instant createdAt,
            Instant expiresAt,
            Ed25519.GeneratedKey signer) {
        byte[] ekPublic = new byte[32];
        ekPublic[0] = 9;
        SubkeyBinding signed = new SubkeyBinding(
                new UserId(account.id), serial, ekId, SubkeyBinding.KEM, ekPublic, createdAt, expiresAt);
        Map<String, Object> body = new HashMap<>();
        body.put("certSerial", Base64Url.encode(serial));
        body.put("ekId", Base64Url.encode(ekId));
        body.put("kem", SubkeyBinding.KEM);
        body.put("ekPublic", Base64Url.encode(ekPublic));
        body.put("createdAt", createdAt.toString());
        body.put("expiresAt", expiresAt.toString());
        body.put("signature", Base64Url.encode(Ed25519.sign(signer.privateKey(), signed.encode())));
        return body;
    }

    private byte[] freshEkId() {
        byte[] ekId = new byte[16];
        ekId[0] = (byte) 0x5e;
        ekId[15] = (byte) ++nextEkId;
        ekId[14] = (byte) (System.nanoTime() & 0x7f);
        return ekId;
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private ResultActions publish(KeysTestClient account, Map<String, Object> body) throws Exception {
        return mvc.perform(account.client.unsafe(post(SUBKEYS), account.session, body));
    }

    @Test
    @DisplayName("KEY-05, SEC-20 : une liaison signée par l'IK certifiée est enregistrée (204) et journalisée")
    void key05SignedBindingIsSaved() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_valide");
        byte[] serial = account.publishIdentity();
        byte[] ekId = freshEkId();
        Instant now = now();

        publish(account, binding(account, serial, ekId, now, now.plus(SubkeyBinding.VALIDITY), account.ik))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql(
                                "SELECT count(*) FROM subkeys WHERE ek_id = :ekId AND user_id = :user AND cert_serial = :serial")
                        .param("ekId", ekId)
                        .param("user", account.id)
                        .param("serial", serial)
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
        assertThat(jdbc.sql(
                                "SELECT details ->> 'ekId' FROM security_events WHERE user_id = :user AND type = 'SUBKEY_PUBLISHED'")
                        .param("user", account.id)
                        .query(String.class)
                        .single())
                .isEqualTo(HexFormat.of().formatHex(ekId));
    }

    @Test
    @DisplayName("KEY-05 : un ekId déjà utilisé, même par un autre compte, donne 400 VALIDATION_FAILED sur ekId")
    void key05ReusedEkIdIsRefused() throws Exception {
        KeysTestClient first = KeysTestClient.account(mvc, mail, "ek_premier");
        KeysTestClient second = KeysTestClient.account(mvc, mail, "ek_second");
        byte[] firstSerial = first.publishIdentity();
        byte[] secondSerial = second.publishIdentity();
        byte[] ekId = freshEkId();
        Instant now = now();
        Instant expires = now.plus(SubkeyBinding.VALIDITY);
        publish(first, binding(first, firstSerial, ekId, now, expires, first.ik))
                .andExpect(status().isNoContent());

        publish(first, binding(first, firstSerial, ekId, now, expires, first.ik))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", contains("ekId")));
        publish(second, binding(second, secondSerial, ekId, now, expires, second.ik))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("ekId")));
    }

    @Test
    @DisplayName("KEY-05 : sans identité publiée, ou avec le certificat d'un autre compte, 404 KEY_NOT_FOUND")
    void key05BindingNeedsTheCallersCurrentCertificate() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_sans_cert");
        KeysTestClient other = KeysTestClient.account(mvc, mail, "ek_autre_cert");
        byte[] otherSerial = other.publishIdentity();
        Instant now = now();
        Instant expires = now.plus(SubkeyBinding.VALIDITY);

        publish(account, binding(account, otherSerial, freshEkId(), now, expires, account.ik))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));
        account.publishIdentity();
        publish(account, binding(account, otherSerial, freshEkId(), now, expires, account.ik))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));
    }

    @Test
    @DisplayName("KEY-05 : une liaison signée par une autre clé que l'IK certifiée donne 422 INVALID_SIGNATURE")
    void key05BindingSignedByAnotherKeyIsRefused() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_forge");
        byte[] serial = account.publishIdentity();
        Instant now = now();

        publish(
                        account,
                        binding(
                                account,
                                serial,
                                freshEkId(),
                                now,
                                now.plus(SubkeyBinding.VALIDITY),
                                Ed25519.generate()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    @DisplayName("ADR 0025, point 7 : createdAt trop loin, validité autre que 45 jours ou X-Wing donnent 400")
    void adr0025BoundsAreEnforced() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_bornes");
        byte[] serial = account.publishIdentity();
        Instant now = now();
        Instant late = now.plusSeconds(301);
        Map<String, Object> xWing =
                binding(account, serial, freshEkId(), now, now.plus(SubkeyBinding.VALIDITY), account.ik);
        xWing.put("kem", "X-WING");

        publish(account, binding(account, serial, freshEkId(), late, late.plus(SubkeyBinding.VALIDITY), account.ik))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("createdAt")));
        publish(
                        account,
                        binding(
                                account,
                                serial,
                                freshEkId(),
                                now,
                                now.plus(SubkeyBinding.VALIDITY).plusSeconds(86_400),
                                account.ik))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("expiresAt")));
        publish(account, xWing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields", contains("kem")));
    }

    @Test
    @DisplayName("QA-05 : sans session 401, sans jeton CSRF 403, en session restreinte 403")
    void qa05AccessIsChecked() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie restricted =
                client.register("ek_restreint@exemple.ca", "ek_restreint").session();
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_csrf");

        mvc.perform(client.unsafe(post(SUBKEYS), null, Map.of()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.unsafe(post(SUBKEYS), restricted, Map.of()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.from(post(SUBKEYS)).cookie(account.session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
