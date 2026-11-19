package fr.vlegall.seal.pki.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.pki.application.port.in.CertificateDetails;
import fr.vlegall.seal.pki.domain.model.AuthorityKey;
import fr.vlegall.seal.pki.domain.model.CertificateRecord;
import fr.vlegall.seal.pki.domain.model.CertificateRecords;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.AuthoritySigner;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-04, ADR 0025 (point 4) : émission, refus d'un second certificat valide, renouvellement après expiration. */
@DisplayName("KEY-04 : émission du certificat d'identité")
class IssueCertificateHandlerTest {

    private static final UserId SUBJECT = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00.123Z");
    private static final byte[] IK = new byte[32];
    private static final byte[] SERIAL = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private final Ed25519.GeneratedKey ca = Ed25519.generate();
    private final FakeCertificates certificates = new FakeCertificates();

    private IssueCertificateHandler handler(Instant now) {
        AuthoritySigner signer = new AuthoritySigner() {
            @Override
            public AuthorityKey key() {
                return new AuthorityKey(ca.publicKey());
            }

            @Override
            public byte[] sign(byte[] message) {
                return Ed25519.sign(ca.privateKey(), message);
            }
        };
        return new IssueCertificateHandler(
                signer, certificates, count -> Arrays.copyOf(SERIAL, count), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("KEY-04 : le certificat émis porte le titulaire, sa clé, l'identifiant de la CA, et la CA le signe")
    void key04IssuedCertificateIsSignedByTheAuthority() {
        CertificateDetails issued =
                handler(NOW).issue(SUBJECT, "valentin.dupont", IK).orElseThrow();

        assertThat(issued.serial()).isEqualTo(SERIAL);
        assertThat(issued.subject()).isEqualTo(SUBJECT);
        assertThat(issued.username()).isEqualTo("valentin.dupont");
        assertThat(issued.ikPublic()).isEqualTo(IK);
        assertThat(issued.issuerKeyId()).isEqualTo(new AuthorityKey(ca.publicKey()).keyId());
        assertThat(issued.notBefore()).isEqualTo(NOW);
        assertThat(issued.notAfter()).isEqualTo(NOW.plus(SealCertificate.VALIDITY));
        assertThat(issued.usage()).isEqualTo(SealCertificate.USAGE);
        SealCertificate stored = certificates.inserted.getFirst().certificate();
        assertThat(Ed25519.verify(ca.publicKey(), stored.encode(), issued.signature()))
                .isTrue();
        assertThat(certificates.inserted.getFirst().signature()).isEqualTo(issued.signature());
    }

    @Test
    @DisplayName("ADR 0025, point 4 : tant qu'un certificat est valide, aucun autre n'est émis")
    void adr0025ValidCertificateBlocksANewOne() {
        handler(NOW).issue(SUBJECT, "valentin.dupont", IK);

        assertThat(handler(NOW.plus(Duration.ofDays(396))).issue(SUBJECT, "valentin.dupont", new byte[32]))
                .isEmpty();
        assertThat(certificates.inserted).hasSize(1);
        assertThat(certificates.revoked).isEmpty();
    }

    @Test
    @DisplayName(
            "ADR 0025, point 4 : après l'expiration, un nouveau certificat est émis et l'ancien révoqué, « remplacé »")
    void adr0025ExpiredCertificateIsSuperseded() {
        handler(NOW).issue(SUBJECT, "valentin.dupont", IK);
        Instant later = NOW.plus(SealCertificate.VALIDITY);

        assertThat(handler(later).issue(SUBJECT, "valentin.dupont", IK)).isPresent();
        assertThat(certificates.revoked).containsExactly(new Revocation(SERIAL, later, RevocationReason.SUPERSEDED));
        assertThat(certificates.inserted).hasSize(2);
    }

    @Test
    @DisplayName("ADR 0025, point 4 : une publication concurrente perdue est refusée comme un second certificat")
    void adr0025ConcurrentPublicationIsRefused() {
        certificates.refuseInsert = true;

        assertThat(handler(NOW).issue(SUBJECT, "valentin.dupont", IK)).isEmpty();
    }

    @Test
    @DisplayName("KEY-04 : le certificat courant est le certificat non révoqué et non expiré")
    void key04CurrentCertificateIsTheValidOne() {
        CurrentCertificateHandler current =
                new CurrentCertificateHandler(certificates, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(current.current(SUBJECT)).isEmpty();

        handler(NOW).issue(SUBJECT, "valentin.dupont", IK);

        assertThat(current.current(SUBJECT).orElseThrow().serial()).isEqualTo(SERIAL);
        assertThat(current.current(SUBJECT).orElseThrow().ikPublic()).isEqualTo(IK);
        // KEY-33 : le certificat courant se relit entier, identifiant et signature de la CA compris.
        assertThat(current.current(SUBJECT).orElseThrow().username()).isEqualTo("valentin.dupont");
        assertThat(current.current(SUBJECT).orElseThrow().signature())
                .isEqualTo(certificates.inserted.getFirst().signature());
        CurrentCertificateHandler later = new CurrentCertificateHandler(
                certificates, Clock.fixed(NOW.plus(SealCertificate.VALIDITY), ZoneOffset.UTC));
        assertThat(later.current(SUBJECT)).isEmpty();
    }

    @Test
    @DisplayName(
            "KEY-12, ADR 0025 (point 4) : après l'expiration, une autre clé n'est pas certifiée, sans sudo (KEY-34)")
    void adr0025RenewalKeepsTheSameKey() {
        handler(NOW).issue(SUBJECT, "valentin.dupont", IK);
        Instant later = NOW.plus(SealCertificate.VALIDITY);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;

        assertThat(handler(later).issue(SUBJECT, "valentin.dupont", otherKey)).isEmpty();
        assertThat(certificates.revoked).isEmpty();
        assertThat(certificates.inserted).hasSize(1);
    }

    private record Revocation(byte[] serial, Instant at, RevocationReason reason) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Revocation that
                    && Arrays.equals(serial, that.serial)
                    && at.equals(that.at)
                    && reason == that.reason;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(serial);
        }
    }

    /** Table identity_certificates en mémoire : au plus un certificat non révoqué par titulaire. */
    private static final class FakeCertificates implements CertificateRepository {
        final List<IssuedCertificate> inserted = new ArrayList<>();
        final List<Revocation> revoked = new ArrayList<>();
        boolean refuseInsert;

        @Override
        public Optional<CertificateRecord> findUnrevoked(UserId subject) {
            return inserted.stream()
                    .map(IssuedCertificate::certificate)
                    .filter(certificate -> certificate.subject().equals(subject))
                    .filter(certificate -> revoked.stream()
                            .noneMatch(revocation -> Arrays.equals(revocation.serial(), certificate.serial())))
                    .map(CertificateRecords::of)
                    .findFirst();
        }

        @Override
        public Optional<IssuedCertificate> findIssued(byte[] serial) {
            return inserted.stream()
                    .filter(issued -> Arrays.equals(issued.certificate().serial(), serial))
                    .findFirst();
        }

        @Override
        public boolean save(IssuedCertificate certificate) {
            if (refuseInsert) {
                return false;
            }
            inserted.add(certificate);
            return true;
        }

        @Override
        public void revoke(byte[] serial, Instant at, RevocationReason reason) {
            revoked.add(new Revocation(serial, at, reason));
        }
    }
}
