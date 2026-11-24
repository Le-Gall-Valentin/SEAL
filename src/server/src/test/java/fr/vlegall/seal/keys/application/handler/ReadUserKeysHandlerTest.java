package fr.vlegall.seal.keys.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.PublishedSubkey;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.model.UserKeys;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.RelationPort;
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

/**
 * KEY-33, API-02 : le compte relit son certificat courant et ses sous-clés valides ; un ami les lit aussi, tout autre
 * compte ne lit rien (ADR 0037, point 7).
 */
class ReadUserKeysHandlerTest {

    private static final UserId OWNER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final UserId OTHER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-000000000b0b"));
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00.123Z");
    private static final byte[] SERIAL = new byte[16];

    private final List<String> calls = new ArrayList<>();
    /** API-02 : l'appelant est-il ami du titulaire (membre d'une conversation commune à la PR 3) ? */
    private boolean related;

    private boolean sharesConversation;

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

    private ReadUserKeysHandler handler() {
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
        RelationPort friendship = (caller, owner) -> {
            calls.add("related " + caller.value() + " " + owner.value());
            return related;
        };
        RelationPort conversation = (caller, owner) -> {
            calls.add("shares " + caller.value() + " " + owner.value());
            return sharesConversation;
        };
        return new ReadUserKeysHandler(
                certification, subkeys, List.of(friendship, conversation), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("KEY-33 : le certificat courant et les sous-clés valides qui lui sont liées, à l'instant présent")
    void key33ReadsTheCurrentCertificateAndItsLiveSubkeys() {
        Optional<UserKeys> keys = handler().read(OWNER, OWNER);

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
    @DisplayName("API-02 : un compte sans lien avec le titulaire ne lit rien, sans même interroger la PKI")
    void api02UnrelatedCallerReadsNothing() {
        assertThat(handler().read(OTHER, OWNER)).isEmpty();
        assertThat(calls)
                .containsExactly(
                        "related " + OTHER.value() + " " + OWNER.value(),
                        "shares " + OTHER.value() + " " + OWNER.value());
    }

    @Test
    @DisplayName("API-02, ADR 0039 (point 7) : un seul lien suffit, l'amitié ou une conversation commune")
    void api02OneRelationIsEnough() {
        sharesConversation = true;

        assertThat(handler().read(OTHER, OWNER)).isPresent();
    }

    @Test
    @DisplayName("API-02 : un ami du titulaire lit son certificat courant et ses sous-clés valides")
    void api02RelatedCallerReadsTheKeys() {
        related = true;

        Optional<UserKeys> keys = handler().read(OTHER, OWNER);

        assertThat(keys).isPresent();
        assertThat(keys.get().subkeys()).containsExactly(subkey);
        assertThat(calls)
                .containsExactly(
                        "related " + OTHER.value() + " " + OWNER.value(),
                        "current " + OWNER.value(),
                        "findLive " + OWNER.value() + " true " + NOW);
    }
}
