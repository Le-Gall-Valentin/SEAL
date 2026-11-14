package fr.vlegall.seal.pki.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-04, KEY-12, ADR 0025 (point 4) : validité et renouvellement du certificat enregistré. */
@DisplayName("KEY-12 : certificat enregistré")
class CertificateRecordTest {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00.123Z");
    private static final Instant NOT_AFTER = NOW.plus(SealCertificate.VALIDITY);
    private static final byte[] IK = new byte[32];
    private static final CertificateRecord RECORD = new CertificateRecord(
            new byte[16], new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c")), IK, NOW, NOT_AFTER);

    @Test
    @DisplayName("KEY-04 : valide de notBefore inclus à notAfter exclu")
    void key04ValidFromNotBeforeUntilNotAfterExcluded() {
        assertThat(RECORD.isValidAt(NOW)).isTrue();
        assertThat(RECORD.isValidAt(NOT_AFTER.minusMillis(1))).isTrue();
        assertThat(RECORD.isValidAt(NOT_AFTER)).isFalse();
        assertThat(RECORD.isValidAt(NOW.minusMillis(1))).isFalse();
    }

    @Test
    @DisplayName("KEY-12 : seul le certificat expiré se renouvelle, et pour la même clé d'identité (KEY-34)")
    void key12OnlyAnExpiredCertificateRenewsWithTheSameKey() {
        byte[] otherKey = IK.clone();
        otherKey[31] = 1;

        assertThat(RECORD.renewableWith(IK.clone(), NOT_AFTER.minusMillis(1))).isFalse();
        assertThat(RECORD.renewableWith(IK.clone(), NOT_AFTER)).isTrue();
        assertThat(RECORD.renewableWith(otherKey, NOT_AFTER)).isFalse();
    }
}
