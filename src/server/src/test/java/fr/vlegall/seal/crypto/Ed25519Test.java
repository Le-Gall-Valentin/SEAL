package fr.vlegall.seal.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.support.ProtocolVectors;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/** QA-01 : Ed25519 par la JCA, sur les vecteurs officiels de la RFC 8032 (section 7.1, tests 1 à 3). */
@DisplayName("QA-01 : Ed25519")
class Ed25519Test {

    /** Le répertoire de travail des tests Maven est src/server. */
    private static final HexFormat HEX = HexFormat.of();

    /** En-tête PKCS#8 d'une clé privée Ed25519 (RFC 8410), suivi de la graine de 32 octets. */
    static byte[] pkcs8(byte[] seed) {
        return Canonical.concat(HEX.parseHex("302e020100300506032b657004220420"), seed);
    }

    private static Stream<JsonNode> rfcTests() {
        return ProtocolVectors.rfc8032().cases("tests");
    }

    private static byte[] hex(JsonNode test, String field) {
        return HEX.parseHex(test.get(field).asString());
    }

    @TestFactory
    @DisplayName("QA-01 : la signature d'un test de la RFC 8032 est reproduite à l'octet près")
    Stream<DynamicTest> qa01SignsLikeTheRfc() {
        return rfcTests()
                .map(test -> DynamicTest.dynamicTest(
                        test.get("name").asString(),
                        () -> assertThat(Ed25519.sign(pkcs8(hex(test, "secretKey")), hex(test, "message")))
                                .isEqualTo(hex(test, "signature"))));
    }

    @TestFactory
    @DisplayName("QA-01 : la signature d'un test de la RFC 8032 est vérifiée avec sa clé publique brute")
    Stream<DynamicTest> qa01VerifiesTheRfc() {
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
        byte[] signature = Ed25519.sign(key.privateKey(), message);
        // y = 2 : encodage canonique, hors des points d'ordre faible, mais d'aucun point de la courbe. Il passe la
        // règle de l'ADR 0025 (point 14) et c'est la JCA qui le refuse, en exception : le chemin à éprouver.
        byte[] offCurve = HEX.parseHex("02" + "00".repeat(31));
        byte[] nonCanonical = HEX.parseHex("ff".repeat(32));

        assertThat(Ed25519.isWeakPoint(offCurve)).isFalse();
        assertThat(Ed25519.verify(offCurve, message, signature)).isFalse();
        assertThat(Ed25519.verify(nonCanonical, message, signature)).isFalse();
    }

    /** L, l'ordre du sous-groupe premier d'Ed25519 (RFC 8032, 5.1). */
    private static final BigInteger GROUP_ORDER =
            BigInteger.TWO.pow(252).add(new BigInteger("27742317777372353535851937790883648493"));

    private static BigInteger littleEndian(byte[] bytes) {
        byte[] bigEndian = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            bigEndian[i] = bytes[bytes.length - 1 - i];
        }
        return new BigInteger(1, bigEndian);
    }

    private static byte[] littleEndian32(BigInteger value) {
        byte[] bigEndian = value.toByteArray();
        byte[] out = new byte[32];
        for (int i = 0; i < 32 && i < bigEndian.length; i++) {
            out[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return out;
    }

    private static boolean jcaAlone(byte[] publicKey, byte[] message, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Ed25519.toSubjectPublicKeyInfo(publicKey))));
        verifier.update(message);
        return verifier.verify(signature);
    }

    @Test
    @DisplayName("ADR 0025, point 14 : un R neutre, que la JCA accepte du titulaire de la clé, est refusé")
    void adr0025NeutralRIsRejectedEvenFromTheKeyHolder() throws Exception {
        // Clé du test 1 de la RFC 8032 : son titulaire calcule a, puis S = k·a mod L pour R = point neutre (r = 0).
        byte[] seed = HEX.parseHex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");
        byte[] publicKey = HEX.parseHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] message = "SEAL".getBytes(StandardCharsets.UTF_8);
        byte[] scalar = Arrays.copyOf(MessageDigest.getInstance("SHA-512").digest(seed), 32);
        scalar[0] &= (byte) 0xf8;
        scalar[31] &= 0x7f;
        scalar[31] |= 0x40;
        byte[] neutral = HEX.parseHex("01" + "00".repeat(31));
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        digest.update(neutral);
        digest.update(publicKey);
        digest.update(message);
        BigInteger k = littleEndian(digest.digest()).mod(GROUP_ORDER);
        byte[] signature = Canonical.concat(
                neutral, littleEndian32(k.multiply(littleEndian(scalar)).mod(GROUP_ORDER)));

        assertThat(jcaAlone(publicKey, message, signature))
                .as("la JCA seule l'accepte")
                .isTrue();
        assertThat(Ed25519.verify(publicKey, message, signature)).isFalse();
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

    /** Les 8 points d'ordre faible et 6 autres encodages des mêmes points (vectors/identity.json, smallOrder). */
    private static List<byte[]> weakEncodings(String section) {
        return ProtocolVectors.identity().smallOrder(section);
    }

    @Test
    @DisplayName("ADR 0025, point 14 : la règle refuse les 14 encodages d'ordre faible ou non canoniques, et eux seuls")
    void adr0025WeakEncodingRuleCoversEveryEncoding() {
        List<byte[]> weak = new ArrayList<>(weakEncodings("points"));
        weak.addAll(weakEncodings("otherEncodings"));

        assertThat(weak).hasSize(14);
        for (byte[] encoding : weak) {
            assertThat(Ed25519.isWeakPoint(encoding))
                    .as(HEX.formatHex(encoding))
                    .isTrue();
        }
        assertThat(Ed25519.isWeakPoint(Ed25519.generate().publicKey())).isFalse();
        assertThat(Ed25519.isWeakPoint(HEX.parseHex("02" + "00".repeat(31)))).isFalse();
    }

    @Test
    @DisplayName("QA-04, ADR 0025 : aucune clé ni aucun R d'ordre faible ne vérifie, alors que la JCA l'accepterait")
    void qa04SmallOrderKeysAndRAreRejected() {
        List<byte[]> weak = new ArrayList<>(weakEncodings("points"));
        weak.addAll(weakEncodings("otherEncodings"));
        byte[] message = "n'importe quel message".getBytes(StandardCharsets.UTF_8);
        Ed25519.GeneratedKey genuine = Ed25519.generate();

        for (byte[] key : weak) {
            for (byte[] r : weak) {
                byte[] signature = Canonical.concat(r, new byte[32]);
                assertThat(Ed25519.verify(key, message, signature))
                        .as("clé %s, R %s", HEX.formatHex(key), HEX.formatHex(r))
                        .isFalse();
            }
        }
        for (byte[] r : weak) {
            assertThat(Ed25519.verify(genuine.publicKey(), message, Canonical.concat(r, new byte[32])))
                    .isFalse();
        }
    }
}
