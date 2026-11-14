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
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/** Compte vérifié, avec sa clé d'identité publiée et certifiée (KEY-03, KEY-04), pour les tests des clés. */
final class KeysTestClient {

    final AuthClient client;
    final UUID id;
    final Cookie session;
    final Ed25519.GeneratedKey ik = Ed25519.generate();

    private final MockMvc mvc;

    private KeysTestClient(MockMvc mvc, AuthClient client, UUID id, Cookie session) {
        this.mvc = mvc;
        this.client = client;
        this.id = id;
        this.session = session;
    }

    /** Compte inscrit et vérifié, sans clé publiée. */
    static KeysTestClient account(MockMvc mvc, RecordingMailSender mail, String name) throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered registered = client.register(name + "@exemple.ca", name);
        return new KeysTestClient(mvc, client, registered.id(), client.verify(registered));
    }

    /** Publie la clé d'identité et renvoie le numéro de série du certificat émis. */
    byte[] publishIdentity() throws Exception {
        JsonNode challenge = AuthClient.body(mvc.perform(client.unsafe(post("/api/v1/keys/challenge"), session, null))
                .andExpect(status().isOk())
                .andReturn());
        byte[] value = Base64Url.decode(challenge.get("challenge").asString());
        byte[] signature =
                Ed25519.sign(ik.privateKey(), new ProofOfPossession(new UserId(id), ik.publicKey(), value).encode());
        JsonNode certificate = AuthClient.body(mvc.perform(client.unsafe(
                        put("/api/v1/keys/identity"),
                        session,
                        Map.of(
                                "ikPublic", Base64Url.encode(ik.publicKey()),
                                "challenge", Base64Url.encode(value),
                                "signature", Base64Url.encode(signature))))
                .andExpect(status().isOk())
                .andReturn());
        return Base64Url.decode(certificate.get("serial").asString());
    }
}
