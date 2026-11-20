package fr.vlegall.seal.keys.application.handler;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.Challenge;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityPublication;
import fr.vlegall.seal.keys.domain.model.IssuedChallenge;
import fr.vlegall.seal.keys.domain.model.ProofOfPossession;
import fr.vlegall.seal.keys.domain.model.SignedStructure;
import fr.vlegall.seal.keys.domain.port.out.AccountPort;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.ChallengeRepository;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** KEY-03, KEY-04, SEC-20 : défi de preuve de possession, puis publication de la clé d'identité. */
@DisplayName("KEY-03 : preuve de possession et publication de l'identité")
class PublishIdentityHandlerTest {

    private static final UserId USER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final UserId OTHER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-000000000b0b"));
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    private final FakeChallenges challenges = new FakeChallenges();
    private final FakeCertification certification = new FakeCertification();
    private final List<UserId> published = new ArrayList<>();
    private final Map<UserId, String> usernames = new HashMap<>(Map.of(USER, "valentin", OTHER, "autre"));
    private final Ed25519.GeneratedKey ik = Ed25519.generate();
    private final List<String> rejectedSignatures = new ArrayList<>();
    private Instant now = NOW;
    private int nextByte = 1;

    private Clock clock() {
        return Clock.fixed(now, ZoneOffset.UTC);
    }

    private CreateChallengeHandler creator() {
        return new CreateChallengeHandler(
                challenges,
                count -> {
                    byte[] bytes = new byte[count];
                    Arrays.fill(bytes, (byte) nextByte++);
                    return bytes;
                },
                clock());
    }

    private PublishIdentityHandler publisher() {
        AccountPort accounts = user -> Optional.ofNullable(usernames.get(user));
        KeyEventPort events = new KeyEventPort() {
            @Override
            public void identityPublished(UserId user, byte[] serial) {
                published.add(user);
            }

            @Override
            public void subkeyPublished(UserId user, byte[] ekId) {}

            @Override
            public void signatureRejected(UserId user, SignedStructure structure) {
                rejectedSignatures.add(user.value() + "/" + structure);
            }
        };
        return new PublishIdentityHandler(challenges, accounts, certification, events, clock());
    }

    private byte[] sign(UserId user, byte[] challenge) {
        return Ed25519.sign(ik.privateKey(), new ProofOfPossession(user, ik.publicKey(), challenge).encode());
    }

    @Test
    @DisplayName("KEY-03 : le défi fait 32 octets, vaut 5 minutes, et seul son SHA-256 est gardé")
    void key03ChallengeIsRandomShortLivedAndHashed() {
        IssuedChallenge issued = creator().create(USER);

        assertThat(issued.value()).hasSize(Challenge.BYTES);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Challenge.VALIDITY));
        assertThat(challenges.live.get(USER).hash()).isEqualTo(Digests.sha256(issued.value()));
    }

    @Test
    @DisplayName("ADR 0025, point 8 : un nouveau défi remplace le précédent")
    void adr0025NewChallengeReplacesThePrevious() {
        byte[] first = creator().create(USER).value();
        byte[] second = creator().create(USER).value();

        assertThat(challenges.live).hasSize(1);
        assertThat(publisher().publish(USER, ik.publicKey(), first, sign(USER, first)))
                .isInstanceOf(IdentityPublication.ChallengeExpired.class);
        assertThat(publisher().publish(USER, ik.publicKey(), second, sign(USER, second)))
                .isInstanceOf(IdentityPublication.Published.class);
    }

    @Test
    @DisplayName("KEY-04, SEC-20 : une preuve valide fait émettre le certificat, et la publication est journalisée")
    void key04ValidProofPublishesTheIdentity() {
        byte[] challenge = creator().create(USER).value();

        IdentityPublication result = publisher().publish(USER, ik.publicKey(), challenge, sign(USER, challenge));

        assertThat(result).isInstanceOf(IdentityPublication.Published.class);
        IdentityCertificate certificate = ((IdentityPublication.Published) result).certificate();
        assertThat(certificate.username()).isEqualTo("valentin");
        assertThat(certificate.ikPublic()).isEqualTo(ik.publicKey());
        assertThat(published).containsExactly(USER);
    }

    @Test
    @DisplayName("KEY-03 : un défi ne sert qu'une fois, même quand sa preuve était fausse")
    void key03ChallengeIsSingleUse() {
        byte[] challenge = creator().create(USER).value();
        byte[] forged = sign(USER, challenge);
        forged[0] ^= 1;

        assertThat(publisher().publish(USER, ik.publicKey(), challenge, forged))
                .isInstanceOf(IdentityPublication.InvalidSignature.class);
        assertThat(publisher().publish(USER, ik.publicKey(), challenge, sign(USER, challenge)))
                .isInstanceOf(IdentityPublication.ChallengeExpired.class);
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("KEY-03 : le défi encore valide d'un autre compte, ou un défi inconnu, ne prouve rien")
    void key03ForeignOrUnknownChallengeIsRefused() {
        byte[] foreign = creator().create(OTHER).value();
        creator().create(USER);

        assertThat(publisher().publish(USER, ik.publicKey(), foreign, sign(USER, foreign)))
                .isInstanceOf(IdentityPublication.ChallengeExpired.class);
        assertThat(publisher().publish(USER, ik.publicKey(), new byte[32], sign(USER, new byte[32])))
                .isInstanceOf(IdentityPublication.ChallengeExpired.class);
        assertThat(challenges.live).containsKeys(USER, OTHER);
    }

    @Test
    @DisplayName("KEY-03 : un défi de 5 minutes est expiré à l'instant de son échéance")
    void key03ExpiredChallengeIsRefused() {
        byte[] mine = creator().create(USER).value();
        now = NOW.plus(Challenge.VALIDITY);

        assertThat(publisher().publish(USER, ik.publicKey(), mine, sign(USER, mine)))
                .isInstanceOf(IdentityPublication.ChallengeExpired.class);
    }

    @Test
    @DisplayName("KEY-03, A09 : une preuve signée pour un autre compte est refusée et signalée")
    void key03ProofForAnotherAccountIsRefused() {
        byte[] challenge = creator().create(USER).value();

        assertThat(publisher().publish(USER, ik.publicKey(), challenge, sign(OTHER, challenge)))
                .isInstanceOf(IdentityPublication.InvalidSignature.class);
        assertThat(rejectedSignatures).containsExactly(USER.value() + "/PROOF_OF_POSSESSION");
    }

    @Test
    @DisplayName("ADR 0025, point 4 : une identité déjà certifiée n'est pas republiée")
    void adr0025AlreadyCertifiedIsRefused() {
        certification.refuse = true;
        byte[] challenge = creator().create(USER).value();

        assertThat(publisher().publish(USER, ik.publicKey(), challenge, sign(USER, challenge)))
                .isInstanceOf(IdentityPublication.Refused.class);
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("A01 : un compte qui n'est plus actif ne fait certifier aucune clé")
    void a01InactiveAccountIsRefused() {
        usernames.remove(USER);
        byte[] challenge = creator().create(USER).value();

        assertThat(publisher().publish(USER, ik.publicKey(), challenge, sign(USER, challenge)))
                .isInstanceOf(IdentityPublication.Refused.class);
        assertThat(certification.issued).isEmpty();
    }

    /** Défi tel que {@code key_challenges} le garde : son haché et son expiration. */
    private record Stored(byte[] hash, Instant expiresAt) {}

    /** Table key_challenges en mémoire : un défi vivant par compte. */
    private final class FakeChallenges implements ChallengeRepository {
        final Map<UserId, Stored> live = new HashMap<>();

        @Override
        public void renew(UserId user, byte[] hash, Instant expiresAt) {
            live.put(user, new Stored(hash.clone(), expiresAt));
        }

        @Override
        public boolean consume(UserId user, byte[] hash, Instant at) {
            Stored stored = live.get(user);
            if (stored == null || !Arrays.equals(stored.hash(), hash) || !at.isBefore(stored.expiresAt())) {
                return false;
            }
            live.remove(user);
            return true;
        }
    }

    private static final class FakeCertification implements CertificationPort {
        final List<IdentityCertificate> issued = new ArrayList<>();
        boolean refuse;

        @Override
        public Optional<IdentityCertificate> certify(UserId subject, String username, byte[] ikPublic) {
            if (refuse) {
                return Optional.empty();
            }
            IdentityCertificate certificate = new IdentityCertificate(
                    new byte[16], new byte[8], subject, username, ikPublic, NOW, NOW, 3, new byte[64]);
            issued.add(certificate);
            return Optional.of(certificate);
        }

        @Override
        public Optional<IdentityCertificate> current(UserId subject) {
            return Optional.empty();
        }
    }
}
