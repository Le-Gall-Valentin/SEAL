package fr.vlegall.seal.pki.application.handler;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.domain.model.CertificateRecord;
import fr.vlegall.seal.pki.domain.model.CertificateRecords;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Table identity_certificates en mémoire : au plus un certificat non révoqué par titulaire (ADR 0025, point 4). */
final class InMemoryCertificates implements CertificateRepository {

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
    public Optional<IssuedCertificate> findCurrentIssued(UserId subject, Instant at) {
        return findUnrevoked(subject)
                .filter(certificate -> certificate.isValidAt(at))
                .flatMap(certificate -> inserted.stream()
                        .filter(issued -> Arrays.equals(issued.certificate().serial(), certificate.serial()))
                        .findFirst());
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

    record Revocation(byte[] serial, Instant at, RevocationReason reason) {
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
}
