package fr.vlegall.seal.keys.infrastructure.web;

import static fr.vlegall.seal.keys.infrastructure.web.KeysTestClient.RESET;
import static fr.vlegall.seal.keys.infrastructure.web.KeysTestClient.vault;
import static fr.vlegall.seal.support.AuthClient.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** KEY-34, AUTH-55, ADR 0036 (point 1), SEC-20, QA-05 : réinitialisation de l'identité, attaquée comme par un tiers. */
@SealIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class IdentityResetIT {

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

    /** Compte vérifié avec son premier coffre (version 1, marqué 1) et son certificat. */
    private KeysTestClient published(String name) throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, name);
        account.saveVault(vault(1, 64, 1));
        account.publishIdentity();
        return account;
    }

    private long unrevoked(KeysTestClient account) {
        return jdbc.sql("SELECT count(*) FROM identity_certificates WHERE user_id = :user AND revoked_at IS NULL")
                .param("user", account.id)
                .query(Long.class)
                .single();
    }

    /** Clé d'identité du certificat courant, relue par {@code GET /users/{id}/keys}. */
    private String currentIk(KeysTestClient account) throws Exception {
        return AuthClient.body(
                        mvc.perform(get("/api/v1/users/" + account.id + "/keys").cookie(account.session))
                                .andExpect(status().isOk())
                                .andReturn())
                .get("certificate")
                .get("ikPublic")
                .asString();
    }

    private JsonNode storedVault(KeysTestClient account) throws Exception {
        return AuthClient.body(mvc.perform(get(KeysTestClient.VAULT).cookie(account.session))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** L'ancienne identité et le premier coffre sont intacts. */
    private void assertUnchanged(KeysTestClient account) throws Exception {
        assertThat(currentIk(account)).isEqualTo(Base64Url.encode(account.ik.publicKey()));
        assertThat(unrevoked(account)).isEqualTo(1L);
        assertThat(storedVault(account).get("vaultVersion").longValue()).isEqualTo(1L);
        assertThat(storedVault(account).get("blob").asString())
                .isEqualTo(vault(1, 64, 1).get("blob"));
    }

    @Test
    @DisplayName("KEY-34, SEC-20 : l'ancien certificat est révoqué (4), le nouveau servi, le coffre remplacé, et la"
            + " réinitialisation journalisée")
    void key34ResetReplacesTheIdentityAndTheVault() throws Exception {
        KeysTestClient account = published("raz_ok");
        byte[] oldSerial = Base64Url.decode(AuthClient.body(
                        mvc.perform(get("/api/v1/users/" + account.id + "/keys").cookie(account.session))
                                .andReturn())
                .get("certificate")
                .get("serial")
                .asString());
        account.publishSubkey(oldSerial, clock.instant().truncatedTo(ChronoUnit.MILLIS));
        mvc.perform(get("/api/v1/users/" + account.id + "/keys").cookie(account.session))
                .andExpect(jsonPath("$.subkeys.length()").value(1));
        Ed25519.GeneratedKey newIk = Ed25519.generate();

        JsonNode certificate = AuthClient.body(account.reset(account.resetBody(newIk, vault(2, 64, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").value(account.id.toString()))
                .andExpect(jsonPath("$.username").value("raz_ok"))
                .andExpect(jsonPath("$.ikPublic").value(Base64Url.encode(newIk.publicKey())))
                .andExpect(jsonPath("$.usage").value(3))
                .andReturn());

        assertThat(currentIk(account)).isEqualTo(Base64Url.encode(newIk.publicKey()));
        // La sous-clé de l'ancien certificat n'est plus servie.
        mvc.perform(get("/api/v1/users/" + account.id + "/keys").cookie(account.session))
                .andExpect(jsonPath("$.subkeys").isEmpty());
        assertThat(jdbc.sql("SELECT revocation_reason FROM identity_certificates WHERE serial = :serial"
                                + " AND revoked_at IS NOT NULL")
                        .param("serial", oldSerial)
                        .query(Integer.class)
                        .single())
                .isEqualTo(4);
        assertThat(unrevoked(account)).isEqualTo(1L);
        assertThat(storedVault(account).get("vaultVersion").longValue()).isEqualTo(2L);
        assertThat(storedVault(account).get("blob").asString())
                .isEqualTo(vault(2, 64, 2).get("blob"));
        assertThat(jdbc.sql("SELECT details ->> 'serial', details ->> 'revokedSerial' FROM security_events"
                                + " WHERE user_id = :user AND type = 'IDENTITY_KEY_RESET'")
                        .param("user", account.id)
                        .query((row, index) -> List.of(row.getString(1), row.getString(2)))
                        .single())
                .containsExactly(
                        HexFormat.of()
                                .formatHex(Base64Url.decode(
                                        certificate.get("serial").asString())),
                        HexFormat.of().formatHex(oldSerial));
    }

    @Test
    @DisplayName(
            "AUTH-55 : hors du mode sudo, 403 REAUTH_REQUIRED sans rien changer ; après /reauth, le même défi passe")
    void auth55ResetNeedsSudo() throws Exception {
        KeysTestClient account = published("raz_sudo");
        clock.advance(Duration.ofMinutes(10));
        Map<String, Object> body = account.resetBody(Ed25519.generate(), vault(2, 64, 2));

        account.reset(body)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        assertUnchanged(account);

        MvcResult reauth = mvc.perform(account.client.unsafe(
                        post("/api/v1/auth/reauth"), account.session, Map.of("password", AuthClient.PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(account.client.unsafe(post(RESET), sessionOf(reauth), body)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("KEY-03, ADR 0036 : preuve fausse 422, défi rejoué 422, coffre hors version 409 ; rien ne change")
    void adr0036RefusalsChangeNothing() throws Exception {
        KeysTestClient account = published("raz_refus");
        Ed25519.GeneratedKey newIk = Ed25519.generate();

        Map<String, Object> forged = account.resetBody(newIk, vault(2, 64, 2));
        forged.put("signature", Base64Url.encode(new byte[64]));
        account.reset(forged)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        account.reset(forged)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        for (long version : new long[] {1, 3}) {
            account.reset(account.resetBody(newIk, vault(version, 64, 2)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("VAULT_VERSION_CONFLICT"));
        }
        assertUnchanged(account);
    }

    @Test
    @DisplayName("KEY-03, ADR 0036 : la même clé d'identité, 403 FORBIDDEN, rien ne change et le défi est consommé")
    void adr0036SameKeyIsRefusedAndConsumesTheChallenge() throws Exception {
        KeysTestClient account = published("raz_meme");
        Map<String, Object> same = account.resetBody(account.ik, vault(2, 64, 2));

        account.reset(same)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertUnchanged(account);

        Map<String, Object> replay =
                KeysTestClient.proof(account.id, Base64Url.decode((String) same.get("challenge")), Ed25519.generate());
        replay.put("vault", vault(2, 64, 2));
        account.reset(replay)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        assertUnchanged(account);
    }

    @Test
    @DisplayName("ADR 0036 : sans coffre, 404 KEY_NOT_FOUND et aucun certificat")
    void adr0036WithoutVaultNothingIsIssued() throws Exception {
        KeysTestClient account = KeysTestClient.account(mvc, mail, "raz_vide");

        account.reset(account.resetBody(Ed25519.generate(), vault(1, 64, 2)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("KEY_NOT_FOUND"));

        assertThat(unrevoked(account)).isZero();
    }

    @Test
    @DisplayName(
            "KEY-34, ADR 0036 (point 2) : un second appareil qui a lu l'ancien coffre reçoit 409 et ne change rien")
    void adr0036StaleDeviceGetsAConflict() throws Exception {
        KeysTestClient account = published("raz_second");
        Ed25519.GeneratedKey first = Ed25519.generate();
        account.reset(account.resetBody(first, vault(2, 64, 2))).andExpect(status().isOk());

        account.reset(account.resetBody(Ed25519.generate(), vault(2, 64, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VAULT_VERSION_CONFLICT"));

        assertThat(currentIk(account)).isEqualTo(Base64Url.encode(first.publicKey()));
        assertThat(storedVault(account).get("blob").asString())
                .isEqualTo(vault(2, 64, 2).get("blob"));
    }

    @Test
    @DisplayName("KEY-34, A06 : douze réinitialisations simultanées sur un même défi n'en font qu'une")
    void key34ConcurrentResetsOnOneChallengeMakeOne() throws Exception {
        KeysTestClient account = published("raz_course");
        Map<String, Object> body = account.resetBody(Ed25519.generate(), vault(2, 64, 2));
        List<MockHttpServletRequestBuilder> requests = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            requests.add(account.unsafe(post(RESET), body));
        }

        List<Integer> statuses = Race.run(
                requests.size(),
                index -> mvc.perform(requests.get(index))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        // Au-delà des 3 jetons de la limite (ADR 0036, point 7), les envois simultanés reçoivent 429.
        assertThat(statuses)
                .isSubsetOf(200, 422, 429)
                .filteredOn(code -> code == 200)
                .hasSize(1);
        assertThat(unrevoked(account)).isEqualTo(1L);
        assertThat(storedVault(account).get("vaultVersion").longValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("SEC-01 : champ inconnu, clé courte, coffre absent ou blob trop court 400 ; plus de 64 Kio 413")
    void sec01BodyIsValidated() throws Exception {
        KeysTestClient account = published("raz_corps");
        Ed25519.GeneratedKey newIk = Ed25519.generate();

        Map<String, Object> extra = account.resetBody(newIk, vault(2, 64, 2));
        extra.put("userId", account.id.toString());
        Map<String, Object> shortKey = account.resetBody(newIk, vault(2, 64, 2));
        shortKey.put("ikPublic", Base64Url.encode(new byte[31]));
        Map<String, Object> noVault = account.resetBody(newIk, vault(2, 64, 2));
        noVault.remove("vault");
        Map<String, Object> shortBlob = account.resetBody(newIk, vault(2, 15, 2));
        Map<String, Object> huge = vault(2, 16, 2);
        huge.put("blob", "A".repeat(70_000));

        for (Map<String, Object> body : List.of(extra, shortKey, noVault, shortBlob)) {
            account.reset(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        account.reset(account.resetBody(newIk, huge))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
        assertUnchanged(account);
    }

    @Test
    @DisplayName("QA-05 : sans session 401, sans jeton CSRF 403, en session restreinte 403")
    void qa05AccessIsChecked() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie restricted =
                client.register("raz_restreint@exemple.ca", "raz_restreint").session();
        KeysTestClient account = published("raz_csrf");
        Map<String, Object> body = account.resetBody(Ed25519.generate(), vault(2, 64, 2));

        mvc.perform(client.unsafe(post(RESET), null, body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.unsafe(post(RESET), restricted, body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.from(post(RESET)).cookie(account.session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertUnchanged(account);
    }

    @Test
    @DisplayName("KEY-03, A09, SEC-21 : une preuve pour un autre compte, ou forgée, donne 422 et une alerte WARN qui"
            + " nomme la route, sans aucune valeur reçue")
    void a09ForeignOrForgedProofIsReportedWithoutValues(CapturedOutput output) throws Exception {
        KeysTestClient account = published("raz_alerte");
        KeysTestClient victim = published("raz_victime");
        Ed25519.GeneratedKey newIk = Ed25519.generate();

        Map<String, Object> foreign = KeysTestClient.proof(victim.id, account.challenge(), newIk);
        foreign.put("vault", vault(2, 64, 2));
        account.reset(foreign)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        // Coffre aux octets aléatoires : des zéros en base64 (« AAAA… ») se retrouveraient n'importe où dans la sortie.
        Map<String, Object> distinctVault = vault(2, 64, 2);
        distinctVault.put("blob", Base64Url.encode(randomBytes(64)));
        distinctVault.put("wrappedVdk", Base64Url.encode(randomBytes(48)));
        Map<String, Object> forged = account.resetBody(newIk, distinctVault);
        forged.put("signature", Base64Url.encode(randomBytes(64)));
        account.reset(forged)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertThat(output.getOut()
                        .lines()
                        .filter(line -> line.contains("WARN")
                                && line.contains("Signature refusée")
                                && line.contains("POST /api/v1/keys/identity/reset")
                                && line.contains(account.id.toString())))
                .hasSize(2);
        assertThat(output.getAll())
                .doesNotContain((String) foreign.get("signature"))
                .doesNotContain((String) forged.get("signature"))
                .doesNotContain((String) forged.get("challenge"))
                .doesNotContain((String) distinctVault.get("blob"))
                .doesNotContain((String) distinctVault.get("wrappedVdk"));
        assertUnchanged(account);
        assertUnchanged(victim);
    }

    @Test
    @DisplayName("KEY-34, ADR 0036 (point 7), SEC-10 : au-delà de 3 réinitialisations réussies par 24 heures, 429 avec"
            + " Retry-After ; un refus ne compte pas")
    void sec10ResetsAreLimited() throws Exception {
        KeysTestClient account = published("raz_limite");
        account.reset(account.resetBody(Ed25519.generate(), vault(3, 64, 9))).andExpect(status().isConflict());
        Ed25519.GeneratedKey last = Ed25519.generate();
        for (int version = 2; version <= 4; version++) {
            last = Ed25519.generate();
            account.reset(account.resetBody(last, vault(version, 64, version))).andExpect(status().isOk());
        }

        account.reset(account.resetBody(Ed25519.generate(), vault(5, 64, 5)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        assertThat(currentIk(account)).isEqualTo(Base64Url.encode(last.publicKey()));
        assertThat(storedVault(account).get("vaultVersion").longValue()).isEqualTo(4L);
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
