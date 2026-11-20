package fr.vlegall.seal.crypto;

import static fr.vlegall.seal.support.ProtocolVectors.bytes;
import static fr.vlegall.seal.support.ProtocolVectors.u64;
import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.ProtocolVectors;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * QA-02, QA-03 (ADR 0030, point 3) : le serveur ne voit jamais {@code SignedPayload}, mais l'encodeur canonique de Java
 * en recompose les octets, ceux de l'AAD et de chaque {@code info_r} à l'identique du générateur Python et du client, et
 * la JCA vérifie la signature Ed25519 que l'émetteur y a posée (PROTOCOL.md, section 6). Aucune classe de production
 * avant le lot 4 : les structures sont recomposées ici, champ par champ.
 */
@DisplayName("QA-02 : enveloppe d'un message")
class EnvelopeVectorsTest {

    private static final ProtocolVectors VECTORS = ProtocolVectors.envelope();
    private static final JsonNode MESSAGE = VECTORS.section("message");
    private static final JsonNode FIELDS = MESSAGE.get("fields");

    private static String text(JsonNode node, String field) {
        return node.get(field).asString();
    }

    private static byte[] signedPayload() {
        List<String> recipients =
                FIELDS.get("recipientIds").valueStream().map(JsonNode::asString).toList();
        return Canonical.struct(
                "SEAL-msg-v1",
                Canonical.u8(FIELDS.get("v").intValue()),
                Canonical.text(text(FIELDS, "suite")),
                Canonical.uuid(text(FIELDS, "messageId")),
                Canonical.uuid(text(FIELDS, "conversationId")),
                Canonical.u8(FIELDS.get("conversationType").intValue()),
                Canonical.uuid(text(FIELDS, "senderId")),
                Canonical.bytes(bytes(FIELDS, "senderCertSerial")),
                Canonical.u64(u64(FIELDS, "sentAt")),
                Canonical.u64(u64(FIELDS, "senderSeq")),
                Canonical.u64(u64(FIELDS, "groupSeq")),
                Canonical.bytes(bytes(FIELDS, "salt")),
                Canonical.list((String recipient) -> Canonical.uuid(recipient), recipients),
                Canonical.text(text(FIELDS, "contentType")),
                Canonical.text(text(FIELDS, "body")));
    }

    private static byte[] aad() {
        return Canonical.struct(
                "SEAL-aad-v1",
                Canonical.u8(FIELDS.get("v").intValue()),
                Canonical.text(text(FIELDS, "suite")),
                Canonical.uuid(text(FIELDS, "messageId")),
                Canonical.uuid(text(FIELDS, "conversationId")),
                Canonical.uuid(text(FIELDS, "senderId")),
                Canonical.bytes(bytes(FIELDS, "senderCertSerial")));
    }

    private static byte[] wrapInfo(JsonNode wrap) {
        return Canonical.struct(
                "SEAL-wrap-v1",
                Canonical.text(text(FIELDS, "suite")),
                Canonical.uuid(text(FIELDS, "messageId")),
                Canonical.uuid(text(FIELDS, "conversationId")),
                Canonical.uuid(text(wrap, "recipientId")),
                Canonical.bytes(bytes(wrap, "ekId")));
    }

    @Test
    @DisplayName("QA-02 : SignedPayload, l'AAD et chaque info_r sont recomposés à l'octet près par l'encodeur de Java")
    void qa02StructuresMatchTheVectors() {
        assertThat(signedPayload()).isEqualTo(bytes(MESSAGE, "signedPayload"));
        assertThat(aad()).isEqualTo(bytes(MESSAGE, "aad"));
        List<JsonNode> wraps = MESSAGE.get("wraps").valueStream().toList();
        assertThat(wraps).hasSize(2);
        for (JsonNode wrap : wraps) {
            assertThat(wrapInfo(wrap)).as(text(wrap, "recipientId")).isEqualTo(bytes(wrap, "info"));
        }
    }

    @Test
    @DisplayName("QA-03 : la signature Ed25519 de SignedPayload, posée par l'émetteur, est vérifiée par la JCA")
    void qa03SenderSignatureVerifiesInJava() {
        byte[] ikPublic = bytes(VECTORS.section("sender"), "ikPublic");
        byte[] payload = bytes(MESSAGE, "signedPayload");
        byte[] signature = bytes(MESSAGE, "signature");
        byte[] altered = payload.clone();
        altered[altered.length - 1] ^= 0x01;

        assertThat(Ed25519.verify(ikPublic, payload, signature)).isTrue();
        assertThat(Ed25519.verify(ikPublic, altered, signature)).isFalse();
    }
}
