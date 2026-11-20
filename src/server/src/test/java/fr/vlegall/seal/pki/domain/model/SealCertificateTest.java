package fr.vlegall.seal.pki.domain.model;

import static fr.vlegall.seal.support.ProtocolVectors.bytes;
import static fr.vlegall.seal.support.ProtocolVectors.instant;
import static fr.vlegall.seal.support.ProtocolVectors.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Canonical;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.support.ProtocolVectors;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;

/** KEY-04, QA-02 : certificat SEAL (PROTOCOL.md, section 4.2) et clé de l'Autorité (section 4.1). */
@DisplayName("KEY-04 : certificat d'identité SEAL")
class SealCertificateTest {

    private static final ProtocolVectors VECTORS = ProtocolVectors.identity();
    private static final UserId SUBJECT = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00.123Z");

    static SealCertificate certificate(JsonNode fields) {
        return new SealCertificate(
                bytes(fields, "serial"),
                bytes(fields, "issuerKeyId"),
                new UserId(uuid(fields, "subjectId")),
                fields.get("username").asString(),
                bytes(fields, "ikPublic"),
                instant(fields, "notBefore"),
                instant(fields, "notAfter"),
                fields.get("usage").intValue());
    }

    @TestFactory
    @DisplayName("QA-02 : un certificat des vecteurs est encodé à l'octet près, et la signature de la CA le vérifie")
    Stream<DynamicTest> qa02CertificatesMatchTheVectors() {
        byte[] authority = VECTORS.publicKey("ca");
        return VECTORS.cases("certificates")
                .map(vector -> DynamicTest.dynamicTest(vector.get("name").asString(), () -> {
                    byte[] encoded = certificate(vector.get("fields")).encode();
                    assertThat(encoded).isEqualTo(bytes(vector, "encoded"));
                    assertThat(Ed25519.verify(authority, encoded, bytes(vector, "signature")))
                            .isTrue();
                }));
    }

    @TestFactory
    @DisplayName("QA-04 : une falsification de certificat est refusée, à la construction ou à la vérification")
    Stream<DynamicTest> qa04CertificateForgeriesAreRejected() {
        return VECTORS.forgeries("certificate")
                .map(forgery -> DynamicTest.dynamicTest(forgery.get("name").asString(), () -> {
                    byte[] verifier = VECTORS.publicKey(forgery.get("verifier").asString());
                    boolean accepted;
                    try {
                        accepted = Ed25519.verify(
                                verifier, certificate(forgery.get("fields")).encode(), bytes(forgery, "signature"));
                    } catch (IllegalArgumentException refusedByTheModel) {
                        accepted = false;
                    }
                    assertThat(accepted).isFalse();
                }));
    }

    @Test
    @DisplayName("KEY-10 : identifiant et empreinte de l'Autorité, calculés comme dans les vecteurs")
    void key10AuthorityKeyMatchesTheVectors() {
        AuthorityKey authority = new AuthorityKey(bytes(VECTORS.authority(), "publicKey"));

        assertThat(authority.keyId()).isEqualTo(bytes(VECTORS.authority(), "keyId"));
        assertThat(authority.fingerprint())
                .isEqualTo(VECTORS.authority().get("fingerprint").asString());
    }

    @Test
    @DisplayName("KEY-04 : un certificat émis vaut 397 jours, usage 0b11, à la milliseconde près")
    void key04IssuedCertificateLasts397Days() {
        SealCertificate issued = SealCertificate.issue(
                new byte[16], new byte[8], SUBJECT, "valentin.dupont", new byte[32], NOW.plusNanos(456_789));

        assertThat(issued.notBefore()).isEqualTo(NOW);
        assertThat(issued.notAfter()).isEqualTo(NOW.plus(Duration.ofDays(397)));
        assertThat(issued.usage()).isEqualTo(SealCertificate.USAGE);
    }

    @Test
    @DisplayName("KEY-04 : tailles et durée hors du format refusées à la construction")
    void key04MalformedCertificateIsRefused() {
        Instant after = NOW.plus(SealCertificate.VALIDITY);

        assertThatThrownBy(() -> new SealCertificate(
                        new byte[15], new byte[8], SUBJECT, "valentin", new byte[32], NOW, after, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SealCertificate(
                        new byte[16], new byte[7], SUBJECT, "valentin", new byte[32], NOW, after, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SealCertificate(
                        new byte[16], new byte[8], SUBJECT, "valentin", new byte[31], NOW, after, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> new SealCertificate(new byte[16], new byte[8], SUBJECT, "", new byte[32], NOW, after, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SealCertificate(
                        new byte[16], new byte[8], SUBJECT, "valentin", new byte[32], NOW, after.plusMillis(1), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SealCertificate(
                        new byte[16], new byte[8], SUBJECT, "valentin", new byte[32], NOW, after, 256))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KEY-04 : le certificat garde ses octets, même si l'appelant modifie les siens")
    void key04CertificateIsImmutable() {
        byte[] ikPublic = new byte[32];
        SealCertificate issued = SealCertificate.issue(new byte[16], new byte[8], SUBJECT, "valentin", ikPublic, NOW);
        byte[] before = issued.encode();
        ikPublic[0] = 1;
        issued.ikPublic()[1] = 1;
        issued.serial()[0] = 1;

        assertThat(issued.encode()).isEqualTo(before);
        assertThat(issued)
                .isEqualTo(SealCertificate.issue(new byte[16], new byte[8], SUBJECT, "valentin", new byte[32], NOW));
        assertThat(issued)
                .hasSameHashCodeAs(
                        SealCertificate.issue(new byte[16], new byte[8], SUBJECT, "valentin", new byte[32], NOW));
    }

    @Test
    @DisplayName(
            "KEY-10 : la clé de l'Autorité fait 32 octets, se compare par ses octets et s'affiche par son empreinte")
    void key10AuthorityKeyIsAValue() {
        byte[] raw = bytes(VECTORS.authority(), "publicKey");
        AuthorityKey authority = new AuthorityKey(raw);
        raw[0] ^= 1;

        assertThat(authority.publicKey()).isEqualTo(bytes(VECTORS.authority(), "publicKey"));
        assertThat(authority).isEqualTo(new AuthorityKey(bytes(VECTORS.authority(), "publicKey")));
        assertThat(authority).hasSameHashCodeAs(new AuthorityKey(bytes(VECTORS.authority(), "publicKey")));
        assertThat(authority).isNotEqualTo(new AuthorityKey(raw)).isNotEqualTo(raw);
        assertThat(authority.toString()).contains(authority.fingerprint());
        assertThatThrownBy(() -> new AuthorityKey(new byte[31])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KEY-04 : un certificat s'affiche par son numéro de série, et ne vaut que pour un autre certificat")
    void key04CertificateShowsItsSerial() {
        SealCertificate issued =
                SealCertificate.issue(new byte[16], new byte[8], SUBJECT, "valentin", new byte[32], NOW);

        assertThat(issued.toString())
                .contains("0".repeat(32))
                .contains(SUBJECT.value().toString());
        assertThat(issued).isNotEqualTo(issued.encode());
    }

    @TestFactory
    @DisplayName("KEY-33 : un certificat des vecteurs se relit depuis ses octets signés, champ par champ")
    Stream<DynamicTest> key33CertificatesDecodeFromTheirBytes() {
        return VECTORS.cases("certificates")
                .map(vector -> DynamicTest.dynamicTest(vector.get("name").asString(), () -> {
                    SealCertificate decoded = SealCertificate.decode(bytes(vector, "encoded"));
                    SealCertificate expected = certificate(vector.get("fields"));

                    assertThat(decoded).isEqualTo(expected);
                    assertThat(decoded.username()).isEqualTo(expected.username());
                    assertThat(decoded.subject()).isEqualTo(expected.subject());
                    assertThat(decoded.usage()).isEqualTo(expected.usage());
                }));
    }

    @Test
    @DisplayName("KEY-33 : des octets tronqués, prolongés, d'une autre structure ou hors du format sont refusés")
    void key33MalformedBytesAreRefused() {
        byte[] encoded = bytes(VECTORS.cases("certificates").findFirst().orElseThrow(), "encoded");
        byte[] proof = bytes(VECTORS.cases("proofs").findFirst().orElseThrow(), "encoded");
        Instant notBefore = Instant.ofEpochMilli(1_794_009_600_000L);
        byte[] tooShort = Canonical.struct(
                SealCertificate.LABEL,
                Canonical.bytes(new byte[16]),
                Canonical.bytes(new byte[8]),
                Canonical.uuid(SUBJECT.value()),
                Canonical.text("valentin"),
                Canonical.bytes(new byte[32]),
                Canonical.u64(notBefore.toEpochMilli()),
                Canonical.u64(notBefore.plus(Duration.ofDays(30)).toEpochMilli()),
                Canonical.u8(SealCertificate.USAGE));

        assertThatThrownBy(() -> SealCertificate.decode(Arrays.copyOf(encoded, encoded.length - 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SealCertificate.decode(Arrays.copyOf(encoded, encoded.length + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SealCertificate.decode(proof)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SealCertificate.decode(tooShort)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KEY-33, ADR 0006 : un identifiant hors de la forme NFC dans les octets signés est refusé")
    void key33NonNfcUsernameIsRefused() {
        Instant notBefore = Instant.ofEpochMilli(1_794_009_600_000L);
        byte[] decomposed = Canonical.struct(
                SealCertificate.LABEL,
                Canonical.bytes(new byte[16]),
                Canonical.bytes(new byte[8]),
                Canonical.uuid(SUBJECT.value()),
                Canonical.bytes("vale\u0301ntin".getBytes(StandardCharsets.UTF_8)),
                Canonical.bytes(new byte[32]),
                Canonical.u64(notBefore.toEpochMilli()),
                Canonical.u64(notBefore.plus(SealCertificate.VALIDITY).toEpochMilli()),
                Canonical.u8(SealCertificate.USAGE));

        assertThatThrownBy(() -> SealCertificate.decode(decomposed)).isInstanceOf(IllegalArgumentException.class);
    }
}
