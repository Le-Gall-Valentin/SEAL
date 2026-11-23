package fr.vlegall.seal.keys.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Ed25519;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityReset;
import fr.vlegall.seal.keys.domain.model.ProofOfPossession;
import fr.vlegall.seal.keys.domain.model.SignedStructure;
import fr.vlegall.seal.keys.domain.model.Vault;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import fr.vlegall.seal.keys.domain.port.out.VaultRepository;
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

/** KEY-34, AUTH-55, ADR 0036 (point 1) : réinitialisation de l'identité, dans l'ordre des contrôles. */
@DisplayName("KEY-34 : réinitialisation de l'identité")
class ResetIdentityHandlerTest {

    private static final UserId USER = new UserId(UUID.fromString("0192f7a1-ffff-7000-8000-00000000a11c"));
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    private final InMemoryChallenges challenges = new InMemoryChallenges();
    private final InMemoryVaults vaults = new InMemoryVaults();
    private final FakeCertification certification = new FakeCertification();
    private final Map<UserId, String> usernames = new HashMap<>(Map.of(USER, "valentin"));
    private final List<String> events = new ArrayList<>();
    private final Ed25519.GeneratedKey newIk = Ed25519.generate();
    private boolean sudo = true;
    private int nextByte = 1;

    private Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private byte[] challenge() {
        return new CreateChallengeHandler(
                        challenges,
                        count -> {
                            byte[] bytes = new byte[count];
                            Arrays.fill(bytes, (byte) nextByte++);
                            return bytes;
                        },
                        clock())
                .create(USER)
                .value();
    }

    private ResetIdentityHandler handler() {
        KeyEventPort journal = new KeyEventPort() {
            @Override
            public void identityPublished(UserId user, byte[] serial) {
                events.add("published");
            }

            @Override
            public void identityReset(UserId user, byte[] serial) {
                events.add("reset/" + serial[0]);
            }

            @Override
            public void subkeyPublished(UserId user, byte[] ekId) {}

            @Override
            public void signatureRejected(UserId user, SignedStructure structure) {
                events.add("rejected/" + structure);
            }
        };
        return new ResetIdentityHandler(
                () -> {
                    if (!sudo) {
                        throw new SealException(ErrorCode.REAUTH_REQUIRED);
                    }
                },
                user -> Optional.ofNullable(usernames.get(user)),
                challenges,
                vaults,
                certification,
                journal,
                clock());
    }

    private byte[] sign(byte[] challenge) {
        return Ed25519.sign(newIk.privateKey(), new ProofOfPossession(USER, newIk.publicKey(), challenge).encode());
    }

    private static Vault vault(long version, int marker) {
        byte[] blob = new byte[16];
        blob[0] = (byte) marker;
        return new Vault(version, new byte[32], new byte[12], new byte[48], new byte[12], blob);
    }

    private IdentityReset reset(byte[] challenge, Vault vault) {
        return handler().reset(USER, newIk.publicKey(), challenge, sign(challenge), vault);
    }

    @Test
    @DisplayName("KEY-34, SEC-20 : en mode sudo, le certificat est remplacé, le coffre écrit et la réinitialisation"
            + " journalisée")
    void key34ValidResetReplacesTheIdentity() {
        vaults.stored.put(USER, vault(4, 1));

        IdentityReset result = reset(challenge(), vault(5, 2));

        assertThat(result).isInstanceOf(IdentityReset.Reset.class);
        IdentityCertificate certificate = ((IdentityReset.Reset) result).certificate();
        assertThat(certificate.ikPublic()).isEqualTo(newIk.publicKey());
        assertThat(certificate.username()).isEqualTo("valentin");
        assertThat(vaults.stored.get(USER).version()).isEqualTo(5);
        assertThat(vaults.stored.get(USER).blob()[0]).isEqualTo((byte) 2);
        assertThat(events).containsExactly("reset/9");
    }

    @Test
    @DisplayName("AUTH-55 : hors du mode sudo, 403 REAUTH_REQUIRED avant tout contrôle ; le défi sert encore")
    void auth55ResetNeedsSudo() {
        vaults.stored.put(USER, vault(1, 1));
        byte[] challenge = challenge();
        sudo = false;

        assertThatThrownBy(() -> reset(challenge, vault(2, 2)))
                .isInstanceOfSatisfying(
                        SealException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.REAUTH_REQUIRED));
        assertThat(challenges.live).containsKey(USER);
        assertThat(vaults.stored.get(USER).version()).isEqualTo(1);
        assertThat(certification.replaced).isEmpty();

        sudo = true;
        assertThat(reset(challenge, vault(2, 2))).isInstanceOf(IdentityReset.Reset.class);
    }

    @Test
    @DisplayName("ADR 0036 : sans coffre enregistré, rien n'est remplacé et le défi reste")
    void adr0036WithoutVaultNothingIsReplaced() {
        byte[] challenge = challenge();

        assertThat(reset(challenge, vault(1, 2))).isInstanceOf(IdentityReset.NoVault.class);
        assertThat(challenges.live).containsKey(USER);
        assertThat(vaults.stored).isEmpty();
        assertThat(certification.replaced).isEmpty();
    }

    @Test
    @DisplayName("KEY-03 : un défi inconnu ou déjà utilisé ne prouve rien")
    void key03UnknownOrUsedChallengeIsRefused() {
        vaults.stored.put(USER, vault(1, 1));
        byte[] challenge = challenge();
        assertThat(reset(challenge, vault(2, 2))).isInstanceOf(IdentityReset.Reset.class);

        assertThat(reset(challenge, vault(3, 3))).isInstanceOf(IdentityReset.ChallengeExpired.class);
        assertThat(reset(new byte[32], vault(3, 3))).isInstanceOf(IdentityReset.ChallengeExpired.class);
        assertThat(vaults.stored.get(USER).version()).isEqualTo(2);
        assertThat(certification.replaced).hasSize(1);
    }

    @Test
    @DisplayName("KEY-03, A09 : une preuve fausse est refusée et signalée ; le défi est consommé, rien n'est écrit")
    void key03ForgedProofIsRefused() {
        vaults.stored.put(USER, vault(1, 1));
        byte[] challenge = challenge();
        byte[] forged = sign(challenge);
        forged[0] ^= 1;

        assertThat(handler().reset(USER, newIk.publicKey(), challenge, forged, vault(2, 2)))
                .isInstanceOf(IdentityReset.InvalidSignature.class);
        assertThat(events).containsExactly("rejected/RESET_PROOF_OF_POSSESSION");
        assertThat(challenges.live).doesNotContainKey(USER);
        assertThat(vaults.stored.get(USER).version()).isEqualTo(1);
        assertThat(certification.replaced).isEmpty();
    }

    @Test
    @DisplayName("KEY-32, ADR 0036 (point 2) : un coffre qui ne suit pas la version enregistrée ne remplace rien")
    void adr0036VaultMustFollowTheStoredVersion() {
        vaults.stored.put(USER, vault(3, 1));

        assertThat(reset(challenge(), vault(3, 2))).isInstanceOf(IdentityReset.VaultConflict.class);
        assertThat(reset(challenge(), vault(5, 2))).isInstanceOf(IdentityReset.VaultConflict.class);
        assertThat(vaults.stored.get(USER).blob()[0]).isEqualTo((byte) 1);
        assertThat(certification.replaced).isEmpty();
    }

    @Test
    @DisplayName(
            "ADR 0036 : un remplacement refusé (même IK, ou devancé) lève 403 FORBIDDEN pour annuler la transaction")
    void adr0036RefusedReplacementThrows() {
        vaults.stored.put(USER, vault(1, 1));
        certification.refuse = true;
        byte[] challenge = challenge();

        assertThatThrownBy(() -> reset(challenge, vault(2, 2)))
                .isInstanceOfSatisfying(
                        SealException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("A01 : un compte qui n'est plus actif ne réinitialise rien")
    void a01InactiveAccountIsRefused() {
        vaults.stored.put(USER, vault(1, 1));
        usernames.remove(USER);
        byte[] challenge = challenge();

        assertThatThrownBy(() -> reset(challenge, vault(2, 2)))
                .isInstanceOfSatisfying(
                        SealException.class, error -> assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(challenges.live).containsKey(USER);
        assertThat(vaults.stored.get(USER).version()).isEqualTo(1);
        assertThat(certification.replaced).isEmpty();
    }

    /** Table key_vaults en mémoire : version 1 sans coffre, sinon la version enregistrée plus 1. */
    private static final class InMemoryVaults implements VaultRepository {
        final Map<UserId, Vault> stored = new HashMap<>();

        @Override
        public Optional<Vault> find(UserId user) {
            return Optional.ofNullable(stored.get(user));
        }

        @Override
        public boolean save(UserId user, Vault vault) {
            long expected = stored.containsKey(user) ? stored.get(user).version() + 1 : 1;
            if (vault.version() != expected) {
                return false;
            }
            stored.put(user, vault);
            return true;
        }
    }

    private static final class FakeCertification implements CertificationPort {
        final List<IdentityCertificate> replaced = new ArrayList<>();
        boolean refuse;

        @Override
        public Optional<IdentityCertificate> certify(UserId subject, String username, byte[] ikPublic) {
            throw new AssertionError("KEY-34 : la réinitialisation remplace, elle ne publie pas");
        }

        @Override
        public Optional<IdentityCertificate> supersede(UserId subject, String username, byte[] ikPublic) {
            if (refuse) {
                return Optional.empty();
            }
            byte[] serial = new byte[16];
            serial[0] = 9;
            IdentityCertificate certificate = new IdentityCertificate(
                    serial, new byte[8], subject, username, ikPublic, NOW, NOW, 3, new byte[64]);
            replaced.add(certificate);
            return Optional.of(certificate);
        }

        @Override
        public Optional<IdentityCertificate> current(UserId subject) {
            return Optional.empty();
        }
    }
}
