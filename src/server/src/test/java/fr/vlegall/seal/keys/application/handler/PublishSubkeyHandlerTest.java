package fr.vlegall.seal.keys.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.CurrentCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.SignedStructure;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.model.SubkeyPolicy;
import fr.vlegall.seal.keys.domain.model.SubkeyPublication;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
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

/** KEY-05, ADR 0025 (point 7) : publication d'une sous-clé, liée au certificat courant par une signature d'IK. */
@DisplayName("KEY-05 : publication d'une sous-clé")
class PublishSubkeyHandlerTest {

    private static final UserId USER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final UserId OTHER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-000000000b0b"));
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final byte[] SERIAL = filled(16, 7);
    private static final byte[] EK_ID = filled(16, 9);
    private static final byte[] EK_PUBLIC = filled(32, 5);

    private final Ed25519.GeneratedKey ik = Ed25519.generate();
    private final FakeSubkeys subkeys = new FakeSubkeys();
    private final List<byte[]> saved = subkeys.ekIds;
    private final List<byte[]> published = new ArrayList<>();
    private final List<String> rejectedSignatures = new ArrayList<>();
    private Instant now = NOW;
    private Optional<CurrentCertificate> current = Optional.of(new CurrentCertificate(SERIAL, ik.publicKey()));

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    private PublishSubkeyHandler handler() {
        CertificationPort certification = new CertificationPort() {
            @Override
            public Optional<IdentityCertificate> certify(UserId subject, String username, byte[] ikPublic) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<CurrentCertificate> current(UserId subject) {
                return current;
            }
        };
        KeyEventPort events = new KeyEventPort() {
            @Override
            public void identityPublished(UserId user, byte[] serial) {}

            @Override
            public void subkeyPublished(UserId user, byte[] ekId) {
                published.add(ekId);
            }

            @Override
            public void signatureRejected(UserId user, SignedStructure structure) {
                rejectedSignatures.add(user.value() + "/" + structure);
            }
        };
        return new PublishSubkeyHandler(certification, subkeys, events, Clock.fixed(now, ZoneOffset.UTC));
    }

    private byte[] sign(UserId user, Instant createdAt, Instant expiresAt, Ed25519.GeneratedKey key) {
        return sign(user, EK_ID, createdAt, expiresAt, key);
    }

    private byte[] sign(UserId user, byte[] ekId, Instant createdAt, Instant expiresAt, Ed25519.GeneratedKey key) {
        SubkeyBinding binding =
                new SubkeyBinding(user, SERIAL, ekId, SubkeyBinding.KEM, EK_PUBLIC, createdAt, expiresAt);
        return Ed25519.sign(key.privateKey(), binding.encode());
    }

    private SubkeyPublication publish(Instant createdAt, Instant expiresAt, byte[] signature) {
        return handler().publish(USER, SERIAL, EK_ID, EK_PUBLIC, createdAt, expiresAt, signature);
    }

    private SubkeyPublication publishFresh(int ekIdByte) {
        byte[] ekId = filled(16, ekIdByte);
        Instant expires = now.plus(SubkeyBinding.VALIDITY);
        return handler().publish(USER, SERIAL, ekId, EK_PUBLIC, now, expires, sign(USER, ekId, now, expires, ik));
    }

    @Test
    @DisplayName("KEY-05, SEC-20 : une liaison signée par l'IK certifiée est enregistrée et journalisée")
    void key05SignedBindingIsSaved() {
        Instant expires = NOW.plus(SubkeyBinding.VALIDITY);

        assertThat(publish(NOW, expires, sign(USER, NOW, expires, ik))).isInstanceOf(SubkeyPublication.Published.class);
        assertThat(saved).containsExactly(EK_ID);
        assertThat(published).containsExactly(EK_ID);
    }

    @Test
    @DisplayName("KEY-05 : un ekId déjà utilisé est refusé")
    void key05ReusedEkIdIsRefused() {
        Instant expires = NOW.plus(SubkeyBinding.VALIDITY);
        publish(NOW, expires, sign(USER, NOW, expires, ik));

        assertThat(publish(NOW, expires, sign(USER, NOW, expires, ik)))
                .isInstanceOf(SubkeyPublication.DuplicateEkId.class);
        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("KEY-05 : sans certificat valide, ou avec le numéro de série d'un autre certificat, 404")
    void key05BindingNeedsTheCurrentCertificate() {
        Instant expires = NOW.plus(SubkeyBinding.VALIDITY);
        byte[] signature = sign(USER, NOW, expires, ik);
        current = Optional.of(new CurrentCertificate(filled(16, 8), ik.publicKey()));

        assertThat(publish(NOW, expires, signature)).isInstanceOf(SubkeyPublication.NoCurrentCertificate.class);
        current = Optional.empty();
        assertThat(publish(NOW, expires, signature)).isInstanceOf(SubkeyPublication.NoCurrentCertificate.class);
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("KEY-05, A09 : une liaison signée par une autre clé, ou pour un autre compte, est refusée et signalée")
    void key05ForgedBindingIsRefused() {
        Instant expires = NOW.plus(SubkeyBinding.VALIDITY);

        assertThat(publish(NOW, expires, sign(USER, NOW, expires, Ed25519.generate())))
                .isInstanceOf(SubkeyPublication.InvalidSignature.class);
        assertThat(publish(NOW, expires, sign(OTHER, NOW, expires, ik)))
                .isInstanceOf(SubkeyPublication.InvalidSignature.class);
        assertThat(saved).isEmpty();
        assertThat(rejectedSignatures)
                .containsExactly(USER.value() + "/SUBKEY_BINDING", USER.value() + "/SUBKEY_BINDING");
    }

    @Test
    @DisplayName("ADR 0025, point 7 : createdAt à plus de 5 minutes de l'horloge du serveur est refusé")
    void adr0025CreatedAtMustBeNearTheServerClock() {
        Duration skew = SubkeyPolicy.CLOCK_SKEW;
        for (Instant createdAt : List.of(NOW.minus(skew), NOW.plus(skew))) {
            Instant expires = createdAt.plus(SubkeyBinding.VALIDITY);
            assertThat(publish(createdAt, expires, sign(USER, createdAt, expires, ik)))
                    .isInstanceOf(SubkeyPublication.Published.class);
            saved.clear();
        }
        for (Instant createdAt :
                List.of(NOW.minus(skew).minusMillis(1), NOW.plus(skew).plusMillis(1))) {
            Instant expires = createdAt.plus(SubkeyBinding.VALIDITY);
            SubkeyPublication result = publish(createdAt, expires, sign(USER, createdAt, expires, ik));
            assertThat(result).isEqualTo(new SubkeyPublication.OutOfBounds(List.of("createdAt")));
        }
    }

    @Test
    @DisplayName("KEY-21, ADR 0025 : une sous-clé vaut 45 jours exactement")
    void key21SubkeyLastsExactly45Days() {
        Instant tooLong = NOW.plus(SubkeyBinding.VALIDITY).plusMillis(1);
        Instant tooShort = NOW.plus(SubkeyBinding.VALIDITY).minusMillis(1);

        assertThat(publish(NOW, tooLong, sign(USER, NOW, tooLong, ik)))
                .isEqualTo(new SubkeyPublication.OutOfBounds(List.of("expiresAt")));
        assertThat(publish(NOW, tooShort, sign(USER, NOW, tooShort, ik)))
                .isEqualTo(new SubkeyPublication.OutOfBounds(List.of("expiresAt")));
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("KEY-05, ADR 0025 (point 7) : des dates extrêmes sont hors des bornes, sans débordement de calcul")
    void adr0025ExtremeDatesAreOutOfBoundsWithoutOverflow() {
        byte[] anySignature = new byte[64];

        assertThat(publish(Instant.MAX, Instant.MAX, anySignature))
                .isEqualTo(new SubkeyPublication.OutOfBounds(List.of("createdAt", "expiresAt")));
        assertThat(publish(Instant.MIN, Instant.MAX, anySignature))
                .isEqualTo(new SubkeyPublication.OutOfBounds(List.of("createdAt", "expiresAt")));
        assertThat(publish(NOW, Instant.MIN, anySignature))
                .isEqualTo(new SubkeyPublication.OutOfBounds(List.of("expiresAt")));
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName(
            "ADR 0025, point 7 : les dates sont ramenées à la milliseconde signée avant contrôle et enregistrement")
    void adr0025DatesAreTruncatedToTheSignedMillisecond() {
        Instant createdAt = NOW.plusNanos(456_789);
        Instant expiresAt = NOW.plus(SubkeyBinding.VALIDITY).plusNanos(999_999);
        Instant signedCreatedAt = NOW;
        Instant signedExpiresAt = NOW.plus(SubkeyBinding.VALIDITY);

        assertThat(publish(createdAt, expiresAt, sign(USER, signedCreatedAt, signedExpiresAt, ik)))
                .isInstanceOf(SubkeyPublication.Published.class);
        assertThat(subkeys.bindings.getFirst().createdAt()).isEqualTo(signedCreatedAt);
        assertThat(subkeys.bindings.getFirst().expiresAt()).isEqualTo(signedExpiresAt);
    }

    @Test
    @DisplayName(
            "KEY-05, ADR 0025 (point 15) : au plus trois sous-clés non expirées, comptées sous le verrou du compte")
    void adr0025AtMostThreeLiveSubkeys() {
        assertThat(publishFresh(1)).isInstanceOf(SubkeyPublication.Published.class);
        assertThat(publishFresh(2)).isInstanceOf(SubkeyPublication.Published.class);
        assertThat(publishFresh(3)).isInstanceOf(SubkeyPublication.Published.class);

        assertThat(publishFresh(4)).isInstanceOf(SubkeyPublication.LimitReached.class);
        assertThat(subkeys.calls).endsWith("lock", "count");
        assertThat(saved).hasSize(SubkeyPolicy.MAX_LIVE);
        assertThat(published).hasSize(SubkeyPolicy.MAX_LIVE);

        now = NOW.plus(SubkeyBinding.VALIDITY);
        assertThat(publishFresh(5)).isInstanceOf(SubkeyPublication.Published.class);
    }

    /** Table subkeys en mémoire : ekId unique, et le verrou du compte noté avant chaque comptage. */
    private static final class FakeSubkeys implements SubkeyRepository {
        final List<byte[]> ekIds = new ArrayList<>();
        final List<SubkeyBinding> bindings = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        @Override
        public void lockAccount(UserId user) {
            calls.add("lock");
        }

        @Override
        public int countLive(UserId user, Instant at) {
            calls.add("count");
            return (int) bindings.stream()
                    .filter(binding -> binding.user().equals(user))
                    .filter(binding -> at.isBefore(binding.expiresAt()))
                    .count();
        }

        @Override
        public boolean save(SubkeyBinding binding, byte[] signature) {
            calls.add("save");
            if (ekIds.stream().anyMatch(ekId -> Arrays.equals(ekId, binding.ekId()))) {
                return false;
            }
            ekIds.add(binding.ekId());
            bindings.add(binding);
            return true;
        }
    }
}
