package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** QA-01 : Ed25519 par la JCA, sur les vecteurs officiels de la RFC 8032 (section 7.1, tests 1 à 3). */
@DisplayName("QA-01 : Ed25519")
class Ed25519Test {

    /** Le répertoire de travail des tests Maven est src/server. */
    private static final Path VECTORS = Path.of("..", "protocol", "vectors", "ed25519-rfc8032.json");

    private static final HexFormat HEX = HexFormat.of();

    /** En-tête PKCS#8 d'une clé privée Ed25519 (RFC 8410), suivi de la graine de 32 octets. */
    static byte[] pkcs8(byte[] seed) {
        return Canonical.concat(HEX.parseHex("302e020100300506032b657004220420"), seed);
    }

    private static Stream<JsonNode> rfcTests() throws IOException {
        JsonNode document = JsonMapper.builder().build().readTree(Files.readString(VECTORS));
        return document.get("tests").valueStream();
    }

    private static byte[] hex(JsonNode test, String field) {
        return HEX.parseHex(test.get(field).asString());
    }

    @TestFactory
    @DisplayName("QA-01 : la signature d'un test de la RFC 8032 est reproduite à l'octet près")
    Stream<DynamicTest> qa01SignsLikeTheRfc() throws IOException {
        return rfcTests()
                .map(test -> DynamicTest.dynamicTest(
                        test.get("name").asString(),
                        () -> assertThat(Ed25519.sign(pkcs8(hex(test, "secretKey")), hex(test, "message")))
                                .isEqualTo(hex(test, "signature"))));
    }

    @TestFactory
    @DisplayName("QA-01 : la signature d'un test de la RFC 8032 est vérifiée avec sa clé publique brute")
    Stream<DynamicTest> qa01VerifiesTheRfc() throws IOException {
        return rfcTests()
                .map(test -> DynamicTest.dynamicTest(
                        test.get("name").asString(),
                        () -> assertThat(Ed25519.verify(
                                        hex(test, "publicKey"), hex(test, "message"), hex(test, "signature")))
                                .isTrue()));
    }

    @Test
    @DisplayName("QA-04 : un octet changé dans le message ou la signature fait échouer la vérification")
    void qa04TamperedMessageOrSignatureIsRejected() {
        Ed25519.GeneratedKey key = Ed25519.generate();
        byte[] message = "SEAL".getBytes(StandardCharsets.UTF_8);
        byte[] signature = Ed25519.sign(key.privateKey(), message);
        byte[] otherMessage = message.clone();
        otherMessage[0] ^= 1;
        byte[] otherSignature = signature.clone();
        otherSignature[63] ^= 1;

        assertThat(Ed25519.verify(key.publicKey(), message, signature)).isTrue();
        assertThat(Ed25519.verify(key.publicKey(), otherMessage, signature)).isFalse();
        assertThat(Ed25519.verify(key.publicKey(), message, otherSignature)).isFalse();
    }

    @Test
    @DisplayName("ADR 0025 : une clé publique ou une signature de mauvaise longueur est refusée, sans exception")
    void adr0025WrongLengthsAreRejected() {
        Ed25519.GeneratedKey key = Ed25519.generate();
        byte[] message = new byte[] {1, 2, 3};
        byte[] signature = Ed25519.sign(key.privateKey(), message);
        byte[] shortKey = new byte[31];
        System.arraycopy(key.publicKey(), 0, shortKey, 0, 31);
        byte[] longSignature = Canonical.concat(signature, new byte[] {0});

        assertThat(Ed25519.verify(shortKey, message, signature)).isFalse();
        assertThat(Ed25519.verify(Canonical.concat(key.publicKey(), new byte[] {0}), message, signature))
                .isFalse();
        assertThat(Ed25519.verify(key.publicKey(), message, longSignature)).isFalse();
        assertThat(Ed25519.verify(key.publicKey(), message, new byte[0])).isFalse();
    }

    @Test
    @DisplayName("ADR 0025 : une clé publique hors de la courbe est refusée, sans exception")
    void adr0025OffCurvePublicKeyIsRejected() {
        Ed25519.GeneratedKey key = Ed25519.generate();
        byte[] message = new byte[] {1};
        byte[] offCurve = HEX.parseHex("ff".repeat(32));

        assertThat(Ed25519.verify(offCurve, message, Ed25519.sign(key.privateKey(), message)))
                .isFalse();
    }

    @Test
    @DisplayName("KEY-10 : une paire générée signe et vérifie, et deux paires diffèrent")
    void key10GeneratedPairsWork() {
        Ed25519.GeneratedKey first = Ed25519.generate();
        Ed25519.GeneratedKey second = Ed25519.generate();
        byte[] message = new byte[] {42};

        assertThat(first.publicKey()).hasSize(Ed25519.PUBLIC_KEY_BYTES);
        assertThat(Ed25519.verify(first.publicKey(), message, Ed25519.sign(first.privateKey(), message)))
                .isTrue();
        assertThat(Ed25519.verify(second.publicKey(), message, Ed25519.sign(first.privateKey(), message)))
                .isFalse();
        assertThat(first.publicKey()).isNotEqualTo(second.publicKey());
    }

    @Test
    @DisplayName("ADR 0025, point 12 : la clé publique passe en SubjectPublicKeyInfo et revient à l'identique")
    void adr0025PublicKeyRoundTripsThroughSpki() {
        Ed25519.GeneratedKey key = Ed25519.generate();

        byte[] spki = Ed25519.toSubjectPublicKeyInfo(key.publicKey());

        assertThat(spki).hasSize(44);
        assertThat(Ed25519.fromSubjectPublicKeyInfo(spki)).isEqualTo(key.publicKey());
    }

    @Test
    @DisplayName("ADR 0025, point 12 : un SubjectPublicKeyInfo qui n'est pas une clé Ed25519 est refusé")
    void adr0025ForeignSpkiIsRefused() {
        byte[] x25519 = HEX.parseHex("302a300506032b656e032100" + "11".repeat(32));

        assertThatThrownBy(() -> Ed25519.fromSubjectPublicKeyInfo(x25519)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ed25519.fromSubjectPublicKeyInfo(new byte[43]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ed25519.toSubjectPublicKeyInfo(new byte[31]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ADR 0025 : une clé privée illisible est une panne, signalée par une exception claire")
    void adr0025UnreadablePrivateKeyFails() {
        assertThatThrownBy(() -> Ed25519.sign(new byte[] {1, 2, 3}, new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ADR 0025 : la paire générée ne laisse pas modifier ses octets de l'extérieur")
    void adr0025GeneratedKeyIsDefensive() {
        Ed25519.GeneratedKey key = Ed25519.generate();
        byte[] exposed = key.publicKey();
        exposed[0] ^= 1;

        assertThat(key.publicKey()).isNotEqualTo(exposed);
    }

    @Test
    @DisplayName("SEC-21 : une paire générée ne montre jamais sa clé privée, et se compare par ses octets")
    void sec21GeneratedKeyHidesItsPrivateKey() {
        Ed25519.GeneratedKey key = Ed25519.generate();
        Ed25519.GeneratedKey copy = new Ed25519.GeneratedKey(key.privateKey(), key.publicKey());

        assertThat(key.toString()).contains("privateKey=***").doesNotContain(HEX.formatHex(key.privateKey()));
        assertThat(key).isEqualTo(copy).hasSameHashCodeAs(copy).isNotEqualTo(Ed25519.generate());
        assertThat(key).isNotEqualTo(key.publicKey());
    }
}
