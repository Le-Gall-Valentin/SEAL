package fr.vlegall.seal.keys.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.CurrentCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
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
    private final List<byte[]> saved = new ArrayList<>();
    private final List<byte[]> published = new ArrayList<>();
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
        SubkeyRepository subkeys = (binding, signature) -> {
            if (saved.stream().anyMatch(ekId -> Arrays.equals(ekId, binding.ekId()))) {
                return false;
            }
            saved.add(binding.ekId());
            return true;
        };
        KeyEventPort events = new KeyEventPort() {
            @Override
            public void identityPublished(UserId user, byte[] serial) {}

            @Override
            public void subkeyPublished(UserId user, byte[] ekId) {
                published.add(ekId);
            }
        };
        return new PublishSubkeyHandler(certification, subkeys, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private byte[] sign(UserId user, Instant createdAt, Instant expiresAt, Ed25519.GeneratedKey key) {
        SubkeyBinding binding =
                new SubkeyBinding(user, SERIAL, EK_ID, SubkeyBinding.KEM, EK_PUBLIC, createdAt, expiresAt);
        return Ed25519.sign(key.privateKey(), binding.encode());
    }

    private SubkeyPublication publish(Instant createdAt, Instant expiresAt, byte[] signature) {
        return handler().publish(USER, SERIAL, EK_ID, EK_PUBLIC, createdAt, expiresAt, signature);
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
    @DisplayName("KEY-05 : une liaison signée par une autre clé, ou pour un autre compte, est refusée")
    void key05ForgedBindingIsRefused() {
        Instant expires = NOW.plus(SubkeyBinding.VALIDITY);

        assertThat(publish(NOW, expires, sign(USER, NOW, expires, Ed25519.generate())))
                .isInstanceOf(SubkeyPublication.InvalidSignature.class);
        assertThat(publish(NOW, expires, sign(OTHER, NOW, expires, ik)))
                .isInstanceOf(SubkeyPublication.InvalidSignature.class);
        assertThat(saved).isEmpty();
    }

    @Test
    @DisplayName("ADR 0025, point 7 : createdAt à plus de 5 minutes de l'horloge du serveur est refusé")
    void adr0025CreatedAtMustBeNearTheServerClock() {
        Duration skew = PublishSubkeyHandler.CLOCK_SKEW;
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
}
