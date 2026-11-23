package fr.vlegall.seal.pki.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.pki.application.port.in.CertificateDetails;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-34, ADR 0036 (points 1 et 5) : le certificat d'un compte remplacé par celui d'une nouvelle clé d'identité. */
@DisplayName("KEY-34 : remplacement du certificat d'identité")
class ReplaceCertificateHandlerTest {

    private static final UserId SUBJECT = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00.123Z");
    private static final byte[] OLD_IK = new byte[32];
    private static final byte[] NEW_IK = filled(7);

    private final TestAuthority ca = new TestAuthority();
    private final InMemoryCertificates certificates = new InMemoryCertificates();
    private byte serialSeed = 1;

    private IssueCertificateHandler issuer(Instant now) {
        return new IssueCertificateHandler(ca, certificates, this::nextSerial, Clock.fixed(now, ZoneOffset.UTC));
    }

    private ReplaceCertificateHandler replacer(Instant now) {
        return new ReplaceCertificateHandler(ca, certificates, this::nextSerial, Clock.fixed(now, ZoneOffset.UTC));
    }

    private byte[] nextSerial(int count) {
        byte[] serial = new byte[count];
        Arrays.fill(serial, serialSeed++);
        return serial;
    }

    @Test
    @DisplayName("KEY-34 : l'ancien certificat est révoqué (4), le nouveau porte la nouvelle IK, un seul non révoqué")
    void key34OldCertificateIsRevokedAndTheNewOneCarriesTheNewKey() {
        byte[] oldSerial = issuer(NOW)
                .issue(SUBJECT, "valentin.dupont", OLD_IK)
                .orElseThrow()
                .serial();
        Instant later = NOW.plus(Duration.ofDays(3));

        CertificateDetails replaced =
                replacer(later).supersede(SUBJECT, "valentin.dupont", NEW_IK).orElseThrow();

        assertThat(certificates.revoked)
                .containsExactly(new InMemoryCertificates.Revocation(oldSerial, later, RevocationReason.SUPERSEDED));
        assertThat(replaced.ikPublic()).isEqualTo(NEW_IK);
        assertThat(replaced.subject()).isEqualTo(SUBJECT);
        assertThat(replaced.username()).isEqualTo("valentin.dupont");
        assertThat(replaced.notBefore()).isEqualTo(later);
        assertThat(replaced.notAfter()).isEqualTo(later.plus(SealCertificate.VALIDITY));
        assertThat(replaced.serial()).isNotEqualTo(oldSerial);
        SealCertificate stored = certificates.inserted.getLast().certificate();
        assertThat(Ed25519.verify(ca.publicKey(), stored.encode(), replaced.signature()))
                .isTrue();
        assertThat(certificates.findUnrevoked(SUBJECT).orElseThrow().ikPublic()).isEqualTo(NEW_IK);
    }

    @Test
    @DisplayName("KEY-34 : un certificat expiré d'une autre clé est remplacé de même")
    void key34ExpiredCertificateIsReplacedToo() {
        issuer(NOW).issue(SUBJECT, "valentin.dupont", OLD_IK);
        Instant later = NOW.plus(SealCertificate.VALIDITY);

        assertThat(replacer(later).supersede(SUBJECT, "valentin.dupont", NEW_IK))
                .isPresent();
        assertThat(certificates.revoked).hasSize(1);
        assertThat(certificates.inserted).hasSize(2);
    }

    @Test
    @DisplayName("KEY-34 : sans certificat courant, le nouveau est émis")
    void key34WithoutCertificateTheNewOneIsIssued() {
        assertThat(replacer(NOW)
                        .supersede(SUBJECT, "valentin.dupont", NEW_IK)
                        .orElseThrow()
                        .ikPublic())
                .isEqualTo(NEW_IK);
        assertThat(certificates.revoked).isEmpty();
        assertThat(certificates.inserted).hasSize(1);
    }

    @Test
    @DisplayName("KEY-34 : même IK, rien n'est émis ni révoqué")
    void key34SameKeyChangesNothing() {
        issuer(NOW).issue(SUBJECT, "valentin.dupont", OLD_IK);

        assertThat(replacer(NOW).supersede(SUBJECT, "valentin.dupont", OLD_IK.clone()))
                .isEmpty();
        assertThat(certificates.revoked).isEmpty();
        assertThat(certificates.inserted).hasSize(1);
    }

    @Test
    @DisplayName("KEY-34, ADR 0036 : un enregistrement devancé rend vide, pour que l'appelant annule la transaction")
    void key34ConcurrentInsertIsReported() {
        certificates.refuseInsert = true;

        assertThat(replacer(NOW).supersede(SUBJECT, "valentin.dupont", NEW_IK)).isEmpty();
    }

    private static byte[] filled(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
