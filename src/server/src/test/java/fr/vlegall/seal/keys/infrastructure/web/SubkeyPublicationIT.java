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
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** KEY-05, KEY-21, SEC-20, QA-05 : publication d'une sous-clé, liée au certificat courant (ADR 0025, point 7). */
@SealIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SubkeyPublicationIT {

    private static final String SUBKEYS = "/api/v1/keys/subkeys";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcClient jdbc;

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

    /** 16 octets tirés au hasard, comme le client : aucune collision entre tests sur la base partagée. */
    private static byte[] freshEkId() {
        byte[] ekId = new byte[SubkeyBinding.EK_ID_BYTES];
        RANDOM.nextBytes(ekId);
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
    @DisplayName("KEY-05, A09 : une liaison signée par une autre clé donne 422 INVALID_SIGNATURE et une alerte WARN"
            + " sans aucune valeur")
    void key05BindingSignedByAnotherKeyIsRefused(CapturedOutput output) throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_forge");
        byte[] serial = account.publishIdentity();
        Instant now = now();
        Map<String, Object> forged =
                binding(account, serial, freshEkId(), now, now.plus(SubkeyBinding.VALIDITY), Ed25519.generate());

        publish(account, forged)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertThat(output.getOut().lines())
                .anyMatch(line -> line.contains("WARN")
                        && line.contains("Signature refusée")
                        && line.contains("POST /api/v1/keys/subkeys")
                        && line.contains(account.id.toString()));
        assertThat(output.getAll())
                .doesNotContain((String) forged.get("signature"))
                .doesNotContain((String) forged.get("ekId"))
                .doesNotContain((String) forged.get("ekPublic"));
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

    @Test
    @DisplayName("KEY-05, ADR 0025 (point 7) : une date extrême, en ISO 8601 ou en nombre, donne 400 et jamais 500")
    void adr0025ExtremeDatesAreRefusedWithoutServerError() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_extreme");
        byte[] serial = account.publishIdentity();
        Instant now = now();
        Map<String, Object> body =
                binding(account, serial, freshEkId(), now, now.plus(SubkeyBinding.VALIDITY), account.ik);

        for (Object instantMax :
                List.of("+1000000000-12-31T23:59:59.999999999Z", new BigDecimal("31556889864403199.999999999"))) {
            body.put("createdAt", instantMax);
            publish(account, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields", contains("createdAt", "expiresAt")));
        }
    }

    @Test
    @DisplayName(
            "KEY-05, ADR 0025 (point 7) : des dates au-delà de la milliseconde sont gardées à la milliseconde signée")
    void adr0025SubMillisecondDatesKeepTheSignedMillisecond() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_microsecondes");
        byte[] serial = account.publishIdentity();
        byte[] ekId = freshEkId();
        Instant now = now();
        Instant expires = now.plus(SubkeyBinding.VALIDITY);
        Map<String, Object> body = binding(account, serial, ekId, now, expires, account.ik);
        // 999,6 µs après la milliseconde signée : arrondie à la microseconde, la base passerait à la suivante.
        body.put("createdAt", now.plusNanos(999_600).toString());
        body.put("expiresAt", expires.plusNanos(999_600).toString());

        publish(account, body).andExpect(status().isNoContent());

        assertThat(jdbc.sql("SELECT created_at, expires_at FROM subkeys WHERE ek_id = :ekId")
                        .param("ekId", ekId)
                        .query((row, number) -> List.of(
                                row.getTimestamp("created_at").toInstant(),
                                row.getTimestamp("expires_at").toInstant()))
                        .single())
                .containsExactly(now, expires);
    }

    @Test
    @DisplayName(
            "KEY-05, ADR 0025 (point 15) : une quatrième sous-clé non expirée donne 403 FORBIDDEN, sans rien écrire")
    void adr0025FourthLiveSubkeyIsForbidden() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_plafond");
        byte[] serial = account.publishIdentity();
        Instant now = now();
        Instant expires = now.plus(SubkeyBinding.VALIDITY);
        for (int i = 0; i < 3; i++) {
            publish(account, binding(account, serial, freshEkId(), now, expires, account.ik))
                    .andExpect(status().isNoContent());
        }

        publish(account, binding(account, serial, freshEkId(), now, expires, account.ik))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(count("SELECT count(*) FROM subkeys WHERE user_id = :user", account))
                .isEqualTo(3L);
        assertThat(count(
                        "SELECT count(*) FROM security_events WHERE user_id = :user AND type = 'SUBKEY_PUBLISHED'",
                        account))
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("KEY-05, A06, ADR 0025 (point 15) : six publications simultanées n'enregistrent que trois sous-clés")
    void adr0025ConcurrentPublicationsKeepTheLimit() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_course");
        byte[] serial = account.publishIdentity();
        Instant now = now();
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(account.client.unsafe(
                    post(SUBKEYS),
                    account.session,
                    binding(account, serial, freshEkId(), now, now.plus(SubkeyBinding.VALIDITY), account.ik)));
        }

        List<Integer> statuses = Race.run(
                requests.size(),
                index -> mvc.perform(requests.get(index))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(statuses)
                .containsOnly(204, 403)
                .filteredOn(code -> code == 204)
                .hasSize(3);
        assertThat(count("SELECT count(*) FROM subkeys WHERE user_id = :user", account))
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("SEC-01 : un champ inconnu ou une clé publique de 31 octets donnent 400 VALIDATION_FAILED")
    void sec01MalformedBindingsAreRefused() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "ek_format");
        byte[] serial = account.publishIdentity();
        Instant now = now();
        Map<String, Object> extra =
                binding(account, serial, freshEkId(), now, now.plus(SubkeyBinding.VALIDITY), account.ik);
        extra.put("userId", UUID.randomUUID().toString());
        Map<String, Object> shortKey =
                binding(account, serial, freshEkId(), now, now.plus(SubkeyBinding.VALIDITY), account.ik);
        shortKey.put("ekPublic", Base64Url.encode(new byte[31]));

        publish(account, extra)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        publish(account, shortKey)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", contains("ekPublic")));
        assertThat(count("SELECT count(*) FROM subkeys WHERE user_id = :user", account))
                .isZero();
    }

    private long count(String sql, KeysTestClient account) {
        return jdbc.sql(sql).param("user", account.id).query(Long.class).single();
    }
}
