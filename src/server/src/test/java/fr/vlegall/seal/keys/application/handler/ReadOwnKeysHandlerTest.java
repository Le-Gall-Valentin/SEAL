package fr.vlegall.seal.keys.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.OwnKeys;
import fr.vlegall.seal.keys.domain.model.PublishedSubkey;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
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

/** KEY-33, A01 : le compte relit son certificat courant et ses sous-clés valides, et seulement les siens. */
class ReadOwnKeysHandlerTest {

    private static final UserId OWNER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final UserId OTHER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-000000000b0b"));
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00.123Z");
    private static final byte[] SERIAL = new byte[16];

    private final List<String> calls = new ArrayList<>();
    private Optional<IdentityCertificate> current = Optional.of(new IdentityCertificate(
            SERIAL,
            new byte[8],
            OWNER,
            "titulaire",
            new byte[32],
            NOW,
            NOW.plus(Duration.ofDays(397)),
            3,
            new byte[64]));
    private final PublishedSubkey subkey = new PublishedSubkey(
            new SubkeyBinding(
                    OWNER,
                    SERIAL,
                    new byte[16],
                    SubkeyBinding.KEM,
                    new byte[32],
                    NOW,
                    NOW.plus(SubkeyBinding.VALIDITY)),
            new byte[64]);

    private ReadOwnKeysHandler handler() {
        CertificationPort certification = new CertificationPort() {
            @Override
            public Optional<IdentityCertificate> certify(UserId subject, String username, byte[] ikPublic) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<IdentityCertificate> current(UserId subject) {
                calls.add("current " + subject.value());
                return current;
            }
        };
        SubkeyRepository subkeys = new SubkeyRepository() {
            @Override
            public void lockAccount(UserId user) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int countLive(UserId user, byte[] certSerial, Instant at) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<PublishedSubkey> findLive(UserId user, byte[] certSerial, Instant at) {
                calls.add("findLive " + user.value() + " " + Arrays.equals(certSerial, SERIAL) + " " + at);
                return List.of(subkey);
            }

            @Override
            public boolean save(SubkeyBinding binding, byte[] signature) {
                throw new UnsupportedOperationException();
            }
        };
        return new ReadOwnKeysHandler(certification, subkeys, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("KEY-33 : le certificat courant et les sous-clés valides qui lui sont liées, à l'instant présent")
    void key33ReadsTheCurrentCertificateAndItsLiveSubkeys() {
        Optional<OwnKeys> keys = handler().read(OWNER, OWNER);

        assertThat(keys).isPresent();
        assertThat(keys.get().certificate()).isEqualTo(current.orElseThrow());
        assertThat(keys.get().subkeys()).containsExactly(subkey);
        assertThat(calls).containsExactly("current " + OWNER.value(), "findLive " + OWNER.value() + " true " + NOW);
    }

    @Test
    @DisplayName("KEY-33 : sans certificat courant, rien, et aucune sous-clé n'est cherchée")
    void key33NoCurrentCertificateReadsNothing() {
        current = Optional.empty();

        assertThat(handler().read(OWNER, OWNER)).isEmpty();
        assertThat(calls).containsExactly("current " + OWNER.value());
    }

    @Test
    @DisplayName("A01 : un autre compte ne lit rien, sans même interroger la PKI (amis au lot 3)")
    void a01OtherCallerReadsNothing() {
        assertThat(handler().read(OTHER, OWNER)).isEmpty();
        assertThat(calls).isEmpty();
    }
}
