package fr.vlegall.seal.pki.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.pki.application.port.in.IssueCertificateUseCase;
import fr.vlegall.seal.pki.domain.model.CertificateRecords;
import fr.vlegall.seal.pki.domain.model.IssuedCertificate;
import fr.vlegall.seal.pki.domain.model.RevocationReason;
import fr.vlegall.seal.pki.domain.model.SealCertificate;
import fr.vlegall.seal.pki.domain.port.out.CertificateRepository;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** ADR 0025, point 4 : un seul certificat non révoqué par compte, garanti par la base. */
@SealIntegrationTest
class CertificateRepositoryIT {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00.123Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private CertificateRepository certificates;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private IssueCertificateUseCase issuance;

    @Autowired
    private MutableClock clock;

    @AfterEach
    void resetMail() {
        mail.reset();
    }

    private UserId account(String name) throws Exception {
        return new UserId(
                new AuthClient(mvc, mail).register(name + "@exemple.ca", name).id());
    }

    private static IssuedCertificate issued(UserId subject, int serialByte) {
        byte[] serial = new byte[16];
        serial[0] = (byte) serialByte;
        return new IssuedCertificate(
                SealCertificate.issue(serial, new byte[8], subject, "titulaire", new byte[32], NOW), new byte[64]);
    }

    @Test
    @DisplayName("KEY-04 : le certificat enregistré se relit, sans son identifiant, à la milliseconde")
    void key04StoredCertificateReadsBack() throws Exception {
        UserId subject = account("cert_relu");
        IssuedCertificate issued = issued(subject, 1);

        assertThat(certificates.save(issued)).isTrue();

        assertThat(certificates.findUnrevoked(subject)).contains(CertificateRecords.of(issued.certificate()));
        assertThat(jdbc.sql("SELECT cert_bytes FROM identity_certificates WHERE user_id = :user")
                        .param("user", subject.value())
                        .query(byte[].class)
                        .single())
                .isEqualTo(issued.certificate().encode());
    }

    @Test
    @DisplayName("ADR 0025, point 4 : un second certificat non révoqué n'est pas écrit, sans erreur")
    void adr0025SecondUnrevokedCertificateIsNotWritten() throws Exception {
        UserId subject = account("cert_double");
        certificates.save(issued(subject, 2));

        assertThat(certificates.save(issued(subject, 3))).isFalse();

        assertThat(jdbc.sql("SELECT count(*) FROM identity_certificates WHERE user_id = :user")
                        .param("user", subject.value())
                        .query(Long.class)
                        .single())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("ADR 0025, point 4 : un certificat révoqué, motif « remplacé », laisse place au suivant")
    void adr0025RevokedCertificateMakesRoom() throws Exception {
        UserId subject = account("cert_remplace");
        IssuedCertificate first = issued(subject, 4);
        certificates.save(first);
        Instant later = NOW.plus(SealCertificate.VALIDITY);

        certificates.revoke(first.certificate().serial(), later, RevocationReason.SUPERSEDED);

        assertThat(certificates.findUnrevoked(subject)).isEmpty();
        assertThat(certificates.save(issued(subject, 5))).isTrue();
        assertThat(jdbc.sql("SELECT revoked_at, revocation_reason FROM identity_certificates WHERE serial = :serial")
                        .param("serial", first.certificate().serial())
                        .query((row, number) ->
                                row.getTimestamp("revoked_at").toInstant() + " " + row.getInt("revocation_reason"))
                        .single())
                .isEqualTo(later.truncatedTo(ChronoUnit.MICROS) + " 4");
    }

    @Test
    @DisplayName("ADR 0025, point 4, A06 : douze renouvellements simultanés ne laissent qu'un certificat non révoqué")
    void adr0025ConcurrentRenewalsKeepOneUnrevokedCertificate() throws Exception {
        UserId subject = account("cert_course");
        byte[] ikPublic = Ed25519.generate().publicKey();
        assertThat(issuance.issue(subject, "cert_course", ikPublic)).isPresent();
        Instant now = clock.instant();
        jdbc.sql(
                        "UPDATE identity_certificates SET not_before = :notBefore, not_after = :notAfter WHERE user_id = :user")
                .param("notBefore", Timestamp.from(now.minus(SealCertificate.VALIDITY)))
                .param("notAfter", Timestamp.from(now.minusSeconds(1)))
                .param("user", subject.value())
                .update();

        List<Boolean> renewed = Race.run(
                12, index -> issuance.issue(subject, "cert_course", ikPublic).isPresent());

        assertThat(renewed).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(jdbc.sql("SELECT count(*) FILTER (WHERE revoked_at IS NULL) || '/' || count(*)"
                                + " FROM identity_certificates WHERE user_id = :user")
                        .param("user", subject.value())
                        .query(String.class)
                        .single())
                .isEqualTo("1/2");
    }
}
