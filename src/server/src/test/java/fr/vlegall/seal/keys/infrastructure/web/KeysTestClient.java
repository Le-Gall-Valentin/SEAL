package fr.vlegall.seal.keys.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.ProofOfPossession;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** Compte vérifié et sa clé d'identité (KEY-03, KEY-04), pour les tests des clés. */
final class KeysTestClient {

    static final String CHALLENGE = "/api/v1/keys/challenge";
    static final String IDENTITY = "/api/v1/keys/identity";

    final AuthClient client;
    final UUID id;
    final Cookie session;
    final Ed25519.GeneratedKey ik;

    private final MockMvc mvc;

    private KeysTestClient(MockMvc mvc, AuthClient client, UUID id, Cookie session, Ed25519.GeneratedKey ik) {
        this.mvc = mvc;
        this.client = client;
        this.id = id;
        this.session = session;
        this.ik = ik;
    }

    /** Compte inscrit et vérifié, en session complète, sans clé publiée. */
    static KeysTestClient account(MockMvc mvc, RecordingMailSender mail, String name) throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered registered = client.register(name + "@exemple.ca", name);
        return new KeysTestClient(mvc, client, registered.id(), client.verify(registered), Ed25519.generate());
    }

    /** Requête non sûre de ce compte, jeton CSRF compris. */
    MockHttpServletRequestBuilder unsafe(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
        return client.unsafe(request, session, body);
    }

    /** KEY-03 : nouveau défi, qui remplace le précédent. */
    byte[] challenge() throws Exception {
        JsonNode body = AuthClient.body(mvc.perform(unsafe(post(CHALLENGE), null))
                .andExpect(status().isOk())
                .andReturn());
        return Base64Url.decode(body.get("challenge").asString());
    }

    /** Corps de {@code PUT /keys/identity} : preuve signée par {@code key} pour le compte {@code signedFor}. */
    static Map<String, Object> proof(UUID signedFor, byte[] challenge, Ed25519.GeneratedKey key) {
        byte[] signature = Ed25519.sign(
                key.privateKey(), new ProofOfPossession(new UserId(signedFor), key.publicKey(), challenge).encode());
        Map<String, Object> body = new HashMap<>();
        body.put("ikPublic", Base64Url.encode(key.publicKey()));
        body.put("challenge", Base64Url.encode(challenge));
        body.put("signature", Base64Url.encode(signature));
        return body;
    }

    /** Preuve de ce compte, par sa clé d'identité, sur un défi neuf. */
    Map<String, Object> proof() throws Exception {
        return proof(id, challenge(), ik);
    }

    ResultActions putIdentity(Map<String, Object> body) throws Exception {
        return mvc.perform(unsafe(put(IDENTITY), body));
    }

    /** Publie la clé d'identité et renvoie le numéro de série du certificat émis. */
    byte[] publishIdentity() throws Exception {
        JsonNode certificate =
                AuthClient.body(putIdentity(proof()).andExpect(status().isOk()).andReturn());
        return Base64Url.decode(certificate.get("serial").asString());
    }
}
