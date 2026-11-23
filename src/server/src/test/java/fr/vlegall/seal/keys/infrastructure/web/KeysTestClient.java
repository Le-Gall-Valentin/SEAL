package fr.vlegall.seal.keys.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.ProofOfPossession;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Instant;
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
    static final String RESET = "/api/v1/keys/identity/reset";
    static final String VAULT = "/api/v1/keys/vault";
    static final String SUBKEYS = "/api/v1/keys/subkeys";

    private static final SecureRandom RANDOM = new SecureRandom();

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
        return Base64Url.decode(challengeBody().get("challenge").asString());
    }

    /** Réponse entière de {@code POST /keys/challenge}. */
    JsonNode challengeBody() throws Exception {
        return AuthClient.body(mvc.perform(unsafe(post(CHALLENGE), null))
                .andExpect(status().isOk())
                .andReturn());
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

    /**
     * Corps de {@code PUT /keys/vault} : champs aux bonnes tailles, blob de {@code blobBytes} marqué au premier
     * octet.
     */
    static Map<String, Object> vault(long version, int blobBytes, int marker) {
        byte[] blob = new byte[blobBytes];
        blob[0] = (byte) marker;
        Map<String, Object> body = new HashMap<>();
        body.put("vaultVersion", version);
        body.put("salt", Base64Url.encode(new byte[32]));
        body.put("nonceK", Base64Url.encode(new byte[12]));
        body.put("wrappedVdk", Base64Url.encode(new byte[48]));
        body.put("nonceB", Base64Url.encode(new byte[12]));
        body.put("blob", Base64Url.encode(blob));
        return body;
    }

    /** KEY-32 : écrit le coffre de ce compte. */
    void saveVault(Map<String, Object> vault) throws Exception {
        mvc.perform(unsafe(put(VAULT), vault)).andExpect(status().isNoContent());
    }

    /** KEY-34 : corps de {@code POST /keys/identity/reset}, preuve de {@code key} sur un défi neuf et coffre. */
    Map<String, Object> resetBody(Ed25519.GeneratedKey key, Map<String, Object> vault) throws Exception {
        Map<String, Object> body = proof(id, challenge(), key);
        body.put("vault", vault);
        return body;
    }

    ResultActions reset(Map<String, Object> body) throws Exception {
        return mvc.perform(unsafe(post(RESET), body));
    }

    /** Corps de {@code POST /keys/subkeys} : liaison de sous-clé (KEY-05) signée par {@code signer}. */
    static Map<String, Object> binding(
            UUID owner, byte[] serial, byte[] ekId, Instant createdAt, Instant expiresAt, Ed25519.GeneratedKey signer) {
        byte[] ekPublic = new byte[32];
        ekPublic[0] = 9;
        SubkeyBinding signed =
                new SubkeyBinding(new UserId(owner), serial, ekId, SubkeyBinding.KEM, ekPublic, createdAt, expiresAt);
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
    static byte[] freshEkId() {
        byte[] ekId = new byte[SubkeyBinding.EK_ID_BYTES];
        RANDOM.nextBytes(ekId);
        return ekId;
    }

    /** KEY-05 : publie une sous-clé du certificat {@code serial}, signée par la clé d'identité de ce compte. */
    void publishSubkey(byte[] serial, Instant now) throws Exception {
        mvc.perform(unsafe(post(SUBKEYS), binding(id, serial, freshEkId(), now, now.plus(SubkeyBinding.VALIDITY), ik)))
                .andExpect(status().isNoContent());
    }
}
