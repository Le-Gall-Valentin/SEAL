package fr.vlegall.seal.keys.infrastructure.web;

import static fr.vlegall.seal.keys.infrastructure.web.KeysTestClient.CHALLENGE;
import static fr.vlegall.seal.keys.infrastructure.web.KeysTestClient.IDENTITY;
import static fr.vlegall.seal.keys.infrastructure.web.KeysTestClient.proof;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.crypto.Canonical;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.Challenge;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** KEY-03, KEY-04, KEY-12, SEC-20, QA-05 : défi puis publication de la clé d'identité, attaquées comme par un tiers. */
@SealIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class IdentityPublicationIT {

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

    private KeysTestClient account(String name) throws Exception {
        return KeysTestClient.account(mvc, mail, name);
    }

    private long certificates(KeysTestClient account, String condition) {
        return jdbc.sql("SELECT count(*) FROM identity_certificates WHERE user_id = :user" + condition)
                .param("user", account.id)
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName("KEY-04 : une preuve valide rend un certificat que la clé de l'Autorité vérifie")
    void key04ValidProofReturnsAVerifiableCertificate() throws Exception {
        KeysTestClient account = account("pop_valide");

        JsonNode certificate = AuthClient.body(account.putIdentity(account.proof())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").value(account.id.toString()))
                .andExpect(jsonPath("$.username").value("pop_valide"))
                .andExpect(jsonPath("$.ikPublic").value(Base64Url.encode(account.ik.publicKey())))
                .andExpect(jsonPath("$.usage").value(3))
                .andReturn());

        SealCertificate rebuilt = new SealCertificate(
                Base64Url.decode(certificate.get("serial").asString()),
                Base64Url.decode(certificate.get("issuerKeyId").asString()),
                new UserId(account.id),
                certificate.get("username").asString(),
                Base64Url.decode(certificate.get("ikPublic").asString()),
                Instant.parse(certificate.get("notBefore").asString()),
                Instant.parse(certificate.get("notAfter").asString()),
                certificate.get("usage").intValue());
        assertThat(rebuilt.issuerKeyId()).isEqualTo(authority.key().keyId());
        assertThat(Ed25519.verify(
                        authority.key().publicKey(),
                        rebuilt.encode(),
                        Base64Url.decode(certificate.get("signature").asString())))
                .isTrue();
        assertThat(jdbc.sql("SELECT details ->> 'serial' FROM security_events"
                                + " WHERE user_id = :user AND type = 'IDENTITY_KEY_PUBLISHED'")
                        .param("user", account.id)
                        .query(String.class)
                        .single())
                .isEqualTo(HexFormat.of().formatHex(rebuilt.serial()));
    }

    @Test
    @DisplayName("KEY-03 : rejouer une preuve acceptée donne 422 CODE_EXPIRED, et la republier 403 FORBIDDEN")
    void key03ReplayAndRepublicationAreRefused() throws Exception {
        KeysTestClient account = account("pop_rejeu");
        Map<String, Object> body = account.proof();
        account.putIdentity(body).andExpect(status().isOk());

        account.putIdentity(body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        account.putIdentity(proof(account.id, account.challenge(), Ed25519.generate()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName(
            "KEY-03, A09 : une preuve pour un autre compte, ou par une autre clé, donne 422 INVALID_SIGNATURE et une"
                    + " alerte WARN sans aucune valeur")
    void key03ForeignOrForgedProofIsRefused(CapturedOutput output) throws Exception {
        KeysTestClient account = account("pop_autre");
        KeysTestClient victim = account("pop_victime");

        account.putIdentity(proof(victim.id, account.challenge(), account.ik))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        // Un défi neuf, demandé après le refus précédent : un seul défi vit par compte (ADR 0025, point 8).
        Map<String, Object> forged = account.proof();
        forged.put("ikPublic", Base64Url.encode(Ed25519.generate().publicKey()));
        account.putIdentity(forged)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));

        assertThat(output.getOut().lines().filter(line -> line.contains("WARN") && line.contains("Signature refusée")))
                .hasSize(2);
        assertThat(output.getAll())
                .doesNotContain((String) forged.get("signature"))
                .doesNotContain((String) forged.get("challenge"))
                .doesNotContain((String) forged.get("ikPublic"));
    }

    @Test
    @DisplayName("KEY-03 : le défi d'un autre compte, encore valide, ne prouve rien (422 CODE_EXPIRED) et reste à lui")
    void key03AnotherAccountsChallengeIsRefused() throws Exception {
        KeysTestClient attacker = account("pop_attaquant");
        KeysTestClient victim = account("pop_cible");
        byte[] stolen = victim.challenge();
        attacker.challenge();

        attacker.putIdentity(proof(attacker.id, stolen, attacker.ik))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        victim.putIdentity(proof(victim.id, stolen, victim.ik)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("KEY-03 : un défi de plus de 5 minutes est expiré")
    void key03ChallengeExpiresAfterFiveMinutes() throws Exception {
        KeysTestClient account = account("pop_expire");
        byte[] challenge = account.challenge();
        clock.advance(Challenge.VALIDITY);

        account.putIdentity(proof(account.id, challenge, account.ik))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName("KEY-03, A06 : douze preuves simultanées sur un même défi n'émettent qu'un certificat")
    void key03ConcurrentProofsOnOneChallengeIssueOneCertificate() throws Exception {
        KeysTestClient account = account("pop_course");
        Map<String, Object> body = account.proof();
        List<MockHttpServletRequestBuilder> requests = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            requests.add(account.unsafe(put(IDENTITY), body));
        }

        List<Integer> statuses = Race.run(
                requests.size(),
                index -> mvc.perform(requests.get(index))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(statuses)
                .containsOnly(200, 422)
                .filteredOn(code -> code == 200)
                .hasSize(1);
        assertThat(certificates(account, "")).isEqualTo(1L);
    }

    @Test
    @DisplayName("KEY-12, ADR 0025 (point 4) : après l'expiration, la même clé est recertifiée et l'ancien certificat"
            + " révoqué ; une autre clé reçoit 403 (KEY-34)")
    void key12RenewalAfterExpiryKeepsTheSameKey() throws Exception {
        KeysTestClient account = account("pop_renouvelle");
        byte[] first = account.publishIdentity();
        // Le certificat vieillit en base : avancer l'horloge ferait aussi tomber toute session (AUTH-52).
        Instant now = clock.instant();
        jdbc.sql(
                        "UPDATE identity_certificates SET not_before = :notBefore, not_after = :notAfter WHERE serial = :serial")
                .param("notBefore", Timestamp.from(now.minus(SealCertificate.VALIDITY)))
                .param("notAfter", Timestamp.from(now.minusSeconds(1)))
                .param("serial", first)
                .update();

        account.putIdentity(proof(account.id, account.challenge(), Ed25519.generate()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        byte[] renewed = account.publishIdentity();

        assertThat(renewed).isNotEqualTo(first);
        assertThat(jdbc.sql("SELECT revocation_reason FROM identity_certificates WHERE serial = :serial")
                        .param("serial", first)
                        .query(Integer.class)
                        .single())
                .isEqualTo(RevocationReason.SUPERSEDED.code());
        assertThat(certificates(account, " AND revoked_at IS NULL")).isEqualTo(1L);
    }

    @Test
    @DisplayName("ADR 0025 : une clé publique hors de la courbe donne 422 INVALID_SIGNATURE, sans 500")
    void adr0025OffCurveKeyIsRefused() throws Exception {
        KeysTestClient account = account("pop_courbe");
        Map<String, Object> body = account.proof();
        // y = 2 : canonique, hors des points d'ordre faible, d'aucun point de la courbe : refusé par la JCA.
        body.put("ikPublic", Base64Url.encode(HexFormat.of().parseHex("02" + "00".repeat(31))));

        account.putIdentity(body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    @DisplayName("QA-04, ADR 0025 : le point neutre, avec R neutre et S nul, ne fait certifier aucune clé (422)")
    void qa04SmallOrderKeyCannotBeCertified() throws Exception {
        KeysTestClient account = account("pop_ordre_faible");
        byte[] identityPoint = HexFormat.of().parseHex("01" + "00".repeat(31));
        Map<String, Object> body = new HashMap<>();
        body.put("ikPublic", Base64Url.encode(identityPoint));
        body.put("challenge", Base64Url.encode(account.challenge()));
        body.put("signature", Base64Url.encode(Canonical.concat(identityPoint, new byte[32])));

        account.putIdentity(body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        assertThat(certificates(account, "")).isZero();
    }

    @Test
    @DisplayName("SEC-01 : longueur fausse, remplissage ou champ inconnu donnent 400 VALIDATION_FAILED")
    void sec01MalformedBodiesAreRefused() throws Exception {
        KeysTestClient account = account("pop_format");
        byte[] challenge = account.challenge();
        Map<String, Object> shortKey = proof(account.id, challenge, account.ik);
        shortKey.put("ikPublic", Base64Url.encode(new byte[31]));
        Map<String, Object> padded = proof(account.id, challenge, account.ik);
        padded.put("challenge", Base64Url.encode(challenge) + "=");
        Map<String, Object> extra = proof(account.id, challenge, account.ik);
        extra.put("userId", account.id.toString());

        account.putIdentity(shortKey)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", contains("ikPublic")));
        account.putIdentity(padded)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("challenge")));
        account.putIdentity(extra)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("QA-05 : sans session 401, sans jeton CSRF 403, en session restreinte 403")
    void qa05AccessIsChecked() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie restricted =
                client.register("pop_restreint@exemple.ca", "pop_restreint").session();
        KeysTestClient account = account("pop_csrf");

        for (MockHttpServletRequestBuilder request :
                new MockHttpServletRequestBuilder[] {post(CHALLENGE), put(IDENTITY)}) {
            mvc.perform(client.unsafe(request, null, Map.of()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        mvc.perform(client.unsafe(post(CHALLENGE), restricted, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.from(post(CHALLENGE)).cookie(account.session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.from(put(IDENTITY)).cookie(account.session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
