package fr.vlegall.seal.keys.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.Challenge;
import fr.vlegall.seal.keys.domain.model.ProofOfPossession;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/** KEY-03, KEY-04, SEC-20, QA-05 : défi puis publication de la clé d'identité, attaquées comme le ferait un tiers. */
@SealIntegrationTest
class IdentityPublicationIT {

    private static final String CHALLENGE = "/api/v1/keys/challenge";
    private static final String IDENTITY = "/api/v1/keys/identity";

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

    private final Ed25519.GeneratedKey ik = Ed25519.generate();

    @AfterEach
    void reset() {
        mail.reset();
        clock.reset();
    }

    private record Account(AuthClient client, UUID id, Cookie session) {}

    private Account account(String name) throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered registered = client.register(name + "@exemple.ca", name);
        return new Account(client, registered.id(), client.verify(registered));
    }

    private byte[] challenge(Account account) throws Exception {
        JsonNode body = AuthClient.body(mvc.perform(account.client().unsafe(post(CHALLENGE), account.session(), null))
                .andExpect(status().isOk())
                .andReturn());
        return Base64Url.decode(body.get("challenge").asString());
    }

    private Map<String, Object> proof(UUID signedFor, byte[] challenge, Ed25519.GeneratedKey key) {
        byte[] signature = Ed25519.sign(
                key.privateKey(), new ProofOfPossession(new UserId(signedFor), key.publicKey(), challenge).encode());
        Map<String, Object> body = new HashMap<>();
        body.put("ikPublic", Base64Url.encode(key.publicKey()));
        body.put("challenge", Base64Url.encode(challenge));
        body.put("signature", Base64Url.encode(signature));
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions publish(Account account, Map<String, Object> body)
            throws Exception {
        return mvc.perform(account.client().unsafe(put(IDENTITY), account.session(), body));
    }

    @Test
    @DisplayName("KEY-04 : une preuve valide rend un certificat que la clé de l'Autorité vérifie")
    void key04ValidProofReturnsAVerifiableCertificate() throws Exception {
        Account account = account("pop_valide");
        byte[] challenge = challenge(account);

        JsonNode certificate = AuthClient.body(publish(account, proof(account.id(), challenge, ik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectId").value(account.id().toString()))
                .andExpect(jsonPath("$.username").value("pop_valide"))
                .andExpect(jsonPath("$.ikPublic").value(Base64Url.encode(ik.publicKey())))
                .andExpect(jsonPath("$.usage").value(3))
                .andReturn());

        SealCertificate rebuilt = new SealCertificate(
                Base64Url.decode(certificate.get("serial").asString()),
                Base64Url.decode(certificate.get("issuerKeyId").asString()),
                new UserId(account.id()),
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
                        .param("user", account.id())
                        .query(String.class)
                        .single())
                .isEqualTo(HexFormat.of().formatHex(rebuilt.serial()));
    }

    @Test
    @DisplayName("KEY-03 : rejouer une preuve acceptée donne 422 CODE_EXPIRED, et la republier 403 FORBIDDEN")
    void key03ReplayAndRepublicationAreRefused() throws Exception {
        Account account = account("pop_rejeu");
        Map<String, Object> body = proof(account.id(), challenge(account), ik);
        publish(account, body).andExpect(status().isOk());

        publish(account, body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        publish(account, proof(account.id(), challenge(account), Ed25519.generate()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("KEY-03 : une preuve signée pour un autre compte, ou par une autre clé, donne 422 INVALID_SIGNATURE")
    void key03ForeignOrForgedProofIsRefused() throws Exception {
        Account account = account("pop_autre");
        UUID victim = account("pop_victime").id();

        publish(account, proof(victim, challenge(account), ik))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
        Map<String, Object> forged = proof(account.id(), challenge(account), ik);
        forged.put("ikPublic", Base64Url.encode(Ed25519.generate().publicKey()));
        publish(account, forged)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    @DisplayName("KEY-03 : le défi d'un autre compte ne prouve rien (422 CODE_EXPIRED), et ne lui est pas consommé")
    void key03AnotherAccountsChallengeIsRefused() throws Exception {
        Account attacker = account("pop_attaquant");
        Account victim = account("pop_cible");
        byte[] stolen = challenge(victim);
        challenge(attacker);

        publish(attacker, proof(attacker.id(), stolen, ik))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        publish(victim, proof(victim.id(), stolen, ik)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("KEY-03 : un défi de plus de 5 minutes est expiré")
    void key03ChallengeExpiresAfterFiveMinutes() throws Exception {
        clock.reset();
        Account account = account("pop_expire");
        byte[] challenge = challenge(account);
        clock.advance(Challenge.VALIDITY);

        publish(account, proof(account.id(), challenge, ik))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName("ADR 0025 : une clé publique hors de la courbe donne 422 INVALID_SIGNATURE, sans 500")
    void adr0025OffCurveKeyIsRefused() throws Exception {
        Account account = account("pop_courbe");
        Map<String, Object> body = proof(account.id(), challenge(account), ik);
        body.put("ikPublic", Base64Url.encode(HexFormat.of().parseHex("ff".repeat(32))));

        publish(account, body)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    @Test
    @DisplayName("SEC-01 : longueur fausse, remplissage ou champ inconnu donnent 400 VALIDATION_FAILED")
    void sec01MalformedBodiesAreRefused() throws Exception {
        Account account = account("pop_format");
        byte[] challenge = challenge(account);
        Map<String, Object> shortKey = proof(account.id(), challenge, ik);
        shortKey.put("ikPublic", Base64Url.encode(new byte[31]));
        Map<String, Object> padded = proof(account.id(), challenge, ik);
        padded.put("challenge", Base64Url.encode(challenge) + "=");
        Map<String, Object> extra = proof(account.id(), challenge, ik);
        extra.put("userId", account.id().toString());

        publish(account, shortKey)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", contains("ikPublic")));
        publish(account, padded)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields", contains("challenge")));
        publish(account, extra)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("QA-05 : sans session 401, sans jeton CSRF 403, en session restreinte 403")
    void qa05AccessIsChecked() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie restricted =
                client.register("pop_restreint@exemple.ca", "pop_restreint").session();
        Account account = account("pop_csrf");

        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
            post(CHALLENGE), put(IDENTITY)
        }) {
            mvc.perform(client.unsafe(request, null, Map.of()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        mvc.perform(client.unsafe(post(CHALLENGE), restricted, null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.from(post(CHALLENGE)).cookie(account.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(client.from(put(IDENTITY)).cookie(account.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
