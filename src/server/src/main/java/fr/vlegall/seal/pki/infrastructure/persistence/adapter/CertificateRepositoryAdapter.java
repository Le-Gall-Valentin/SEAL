package fr.vlegall.seal.pki.infrastructure.persistence.adapter;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.pki.domain.model.CertificateRecord;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code identity_certificates} par {@link JdbcClient}, dans la transaction en cours s'il y en a une. */
@Component
public class CertificateRepositoryAdapter implements CertificateRepository {

    private final JdbcClient jdbc;

    public CertificateRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CertificateRecord> findUnrevoked(UserId subject) {
        return jdbc.sql("SELECT serial, user_id, ik_public, not_before, not_after FROM identity_certificates"
                        + " WHERE user_id = :user AND revoked_at IS NULL")
                .param("user", subject.value())
                .query((row, number) -> new CertificateRecord(
                        row.getBytes("serial"),
                        new UserId(row.getObject("user_id", UUID.class)),
                        row.getBytes("ik_public"),
                        row.getTimestamp("not_before").toInstant(),
                        row.getTimestamp("not_after").toInstant()))
                .optional();
    }

    @Override
    public Optional<IssuedCertificate> findCurrentIssued(UserId subject, Instant at) {
        return jdbc.sql("SELECT cert_bytes, signature FROM identity_certificates"
                        + " WHERE user_id = :user AND revoked_at IS NULL AND not_before <= :at AND not_after > :at")
                .param("user", subject.value())
                .param("at", Timestamp.from(at))
                .query((row, number) -> new IssuedCertificate(
                        SealCertificate.decode(row.getBytes("cert_bytes")), row.getBytes("signature")))
                .optional();
    }

    /**
     * ADR 0025, point 4 : l'index unique partiel {@code one_active_certificate} (V1) départage deux publications
     * concurrentes ; la perdante n'écrit rien, sans erreur qui ferait échouer la transaction.
     */
    @Override
    public boolean save(IssuedCertificate issued) {
        SealCertificate certificate = issued.certificate();
        return jdbc.sql("INSERT INTO identity_certificates"
                                + " (serial, user_id, ik_public, not_before, not_after, cert_bytes, signature)"
                                + " VALUES (:serial, :user, :ikPublic, :notBefore, :notAfter, :certBytes, :signature)"
                                + " ON CONFLICT (user_id) WHERE revoked_at IS NULL DO NOTHING")
                        .param("serial", certificate.serial())
                        .param("user", certificate.subject().value())
                        .param("ikPublic", certificate.ikPublic())
                        .param("notBefore", Timestamp.from(certificate.notBefore()))
                        .param("notAfter", Timestamp.from(certificate.notAfter()))
                        .param("certBytes", certificate.encode())
                        .param("signature", issued.signature())
                        .update()
                == 1;
    }

    @Override
    public void revoke(byte[] serial, Instant at, RevocationReason reason) {
        jdbc.sql("UPDATE identity_certificates SET revoked_at = :at, revocation_reason = :reason"
                        + " WHERE serial = :serial AND revoked_at IS NULL")
                .param("serial", serial)
                .param("at", Timestamp.from(at))
                .param("reason", reason.code())
                .update();
    }
}
