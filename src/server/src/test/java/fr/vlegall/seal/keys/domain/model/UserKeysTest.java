package fr.vlegall.seal.keys.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-33, ADR 0029 (point 4) : clés publiées d'un compte, telles que GET /users/{moi}/keys les sert. */
class UserKeysTest {

    private static final UserId OWNER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final UserId OTHER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-000000000b0b"));
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00.123Z");

    private static byte[] serial(int first) {
        byte[] serial = new byte[16];
        serial[0] = (byte) first;
        return serial;
    }

    private static IdentityCertificate certificate() {
        return new IdentityCertificate(
                serial(1),
                new byte[8],
                OWNER,
                "titulaire",
                new byte[32],
                NOW,
                NOW.plus(Duration.ofDays(397)),
                3,
                new byte[64]);
    }

    private static PublishedSubkey subkey(UserId user, byte[] certSerial, byte[] signature) {
        return new PublishedSubkey(
                new SubkeyBinding(
                        user,
                        certSerial,
                        new byte[16],
                        SubkeyBinding.KEM,
                        new byte[32],
                        NOW,
                        NOW.plus(SubkeyBinding.VALIDITY)),
                signature);
    }

    @Test
    @DisplayName("KEY-33 : les sous-clés servies sont celles du certificat, dans une liste figée")
    void key33SubkeysBelongToTheCertificate() {
        List<PublishedSubkey> served = new ArrayList<>(List.of(subkey(OWNER, serial(1), new byte[64])));

        UserKeys keys = new UserKeys(certificate(), served);
        served.clear();

        assertThat(keys.subkeys()).hasSize(1);
        assertThatThrownBy(() -> keys.subkeys().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("KEY-33, ADR 0029 : une sous-clé liée à un autre certificat ou à un autre compte est refusée")
    void key33ForeignSubkeyIsRefused() {
        assertThatThrownBy(() -> new UserKeys(certificate(), List.of(subkey(OWNER, serial(2), new byte[64]))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserKeys(certificate(), List.of(subkey(OTHER, serial(1), new byte[64]))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("KEY-05 : une sous-clé publiée vaut par sa liaison et sa signature, qu'elle garde à l'abri")
    void key05PublishedSubkeyIsAValue() {
        byte[] signature = new byte[64];
        PublishedSubkey published = subkey(OWNER, serial(1), signature);
        signature[0] = 1;
        published.signature()[1] = 1;

        assertThat(published.signature()).isEqualTo(new byte[64]);
        assertThat(published)
                .isEqualTo(subkey(OWNER, serial(1), new byte[64]))
                .hasSameHashCodeAs(subkey(OWNER, serial(1), new byte[64]))
                .isNotEqualTo(subkey(OWNER, serial(1), signature))
                .isNotEqualTo(subkey(OWNER, serial(2), new byte[64]));
    }
}
