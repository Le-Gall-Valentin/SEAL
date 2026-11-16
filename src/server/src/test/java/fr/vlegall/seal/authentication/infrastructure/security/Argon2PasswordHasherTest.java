package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.common.PasswordPepper;
import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.support.UnpepperedHashes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class Argon2PasswordHasherTest {

    private static final byte[] PEPPER = pepper(1);
    private static final String PASSWORD = "fjord-tuque-raquette";

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(new PasswordPepper(encoded(PEPPER)));

    private static byte[] pepper(int seed) {
        byte[] bytes = new byte[PasswordPepper.BYTES];
        Arrays.fill(bytes, (byte) seed);
        return bytes;
    }

    private static String encoded(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Argon2id aux paramètres d'AUTH-13, sans poivre ni préfixe : la référence de Spring Security. */
    private static Argon2PasswordEncoder plainArgon2() {
        return new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
    }

    @Test
    @DisplayName(
            "AUTH-13 : Argon2id, m = 19456, t = 2, p = 1, sel de 16 octets et sortie de 32, préfixé par {argon2-p1}")
    void auth13HashUsesTheCdcParameters() {
        String encoded = hasher.hash(Password.fromInput(PASSWORD));

        assertThat(encoded).startsWith("{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$");
        String[] parts = encoded.split("\\$");
        assertThat(Base64.getDecoder().decode(parts[4])).hasSize(16);
        assertThat(Base64.getDecoder().decode(parts[5])).hasSize(32);
    }

    @Test
    @DisplayName("AUTH-21 : chaque leurre coûte une vérification Argon2id ; le haché leurre n'est calculé qu'une fois")
    void auth21DecoyCostsOneVerification() {
        int[] encodes = new int[1];
        int[] verifications = new int[1];
        PasswordEncoder counting = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                encodes[0]++;
                return "{argon2-p1}leurre";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                verifications[0]++;
                return false;
            }
        };
        Argon2PasswordHasher decoyed = new Argon2PasswordHasher(PEPPER, counting, 2);

        decoyed.verifyDecoy(Password.fromInput("fjord-tuque-raquette"));
        decoyed.verifyDecoy(Password.fromInput("autre-mot-de-passe-long"));

        assertThat(encodes[0]).as("leurre haché une seule fois").isEqualTo(1);
        assertThat(verifications[0]).as("une vérification par appel").isEqualTo(2);
    }

    @Test
    @DisplayName("AUTH-21 : une demi-paire isolée coûte quand même un calcul Argon2id, dont le résultat est ignoré")
    void auth21LoneSurrogateStillCostsOneVerification() {
        int[] verifications = new int[1];
        PasswordEncoder alwaysMatching = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "{argon2-p1}leurre";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                verifications[0]++;
                assertThat(raw.toString().codePoints().noneMatch(c -> c >= 0xD800 && c <= 0xDFFF))
                        .as("jamais une demi-paire transmise au calcul")
                        .isTrue();
                return true;
            }
        };
        Argon2PasswordHasher measured = new Argon2PasswordHasher(PEPPER, alwaysMatching, 2);
        Password malformed = Password.fromInput(Character.toString(0xD800) + "fjord-tuque-raquette");

        assertThat(measured.matches(malformed, "{argon2}x")).isFalse();
        measured.verifyDecoy(malformed);

        assertThat(verifications[0])
                .as("un calcul par appel, comme pour un mot de passe bien formé")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("AUTH-13 : seul un haché Argon2id aux paramètres de SEAL est reconnu, jamais un texte en clair")
    void auth13OnlyItsOwnHashesAreRecognised() {
        assertThat(hasher.recognises(hasher.hash(Password.fromInput("fjord-tuque-raquette"))))
                .isTrue();
        assertThat(hasher.recognises("fjord-tuque-raquette")).isFalse();
        assertThat(hasher.recognises("{argon2}$argon2id$v=19$m=4096,t=3,p=1$c2Vs$aGFjaGU"))
                .isFalse();
        assertThat(hasher.recognises("{bcrypt}$2a$10$abcdefghijklmnopqrstuv")).isFalse();
        assertThat(hasher.recognises("{argon2}$argon2id$v=19$m=19456,t=2,p=1$")).isFalse();
        assertThat(hasher.recognises("{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$"))
                .isFalse();
        // ADR 0026, point 4 : un haché complet d'avant le poivre n'est plus reconnu.
        assertThat(hasher.recognises(legacyHash())).isFalse();
    }

    /** Haché d'avant l'ADR 0026 : Argon2id sans poivre, préfixé par {argon2}. */
    private static String legacyHash() {
        return UnpepperedHashes.of(PASSWORD);
    }

    @Test
    @DisplayName("AUTH-13, ADR 0026 : le haché est Argon2id sur base64(HMAC-SHA256(poivre, mot de passe))")
    void auth13PepperEntersTheHash() {
        String encoded = hasher.hash(Password.fromInput(PASSWORD));
        String argon2 = encoded.substring("{argon2-p1}".length());
        String peppered = encoded(Digests.hmacSha256(PEPPER, PASSWORD.getBytes(StandardCharsets.UTF_8)));

        assertThat(plainArgon2().matches(PASSWORD, argon2)).as("sans poivre").isFalse();
        assertThat(plainArgon2().matches(peppered, argon2))
                .as("construction de l'ADR 0026")
                .isTrue();
    }

    @Test
    @DisplayName("AUTH-13, ADR 0026 : un haché ne se vérifie qu'avec son poivre")
    void auth13HashOnlyMatchesWithItsPepper() {
        String encoded = hasher.hash(Password.fromInput(PASSWORD));
        Argon2PasswordHasher otherPepper = new Argon2PasswordHasher(new PasswordPepper(encoded(pepper(2))));

        assertThat(hasher.matches(Password.fromInput(PASSWORD), encoded)).isTrue();
        assertThat(otherPepper.matches(Password.fromInput(PASSWORD), encoded)).isFalse();
    }

    @Test
    @DisplayName("ADR 0026, point 4 : un haché d'avant le poivre est refusé comme un mauvais mot de passe, sans erreur")
    void adr0026LegacyHashIsRefused() {
        assertThat(hasher.matches(Password.fromInput(PASSWORD), legacyHash())).isFalse();
    }

    @Test
    @DisplayName("AUTH-21, ADR 0026 : un haché non reconnu coûte une vérification, sur le leurre, jamais sur lui")
    void auth21UnrecognisedHashCostsOneDecoyVerification() {
        List<String> verified = new ArrayList<>();
        PasswordEncoder recording = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "{argon2-p1}leurre";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                verified.add(encoded);
                return true;
            }
        };
        Argon2PasswordHasher measured = new Argon2PasswordHasher(PEPPER, recording, 2);

        assertThat(measured.matches(
                        Password.fromInput(PASSWORD), "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c2Vs$aGFjaGU"))
                .isFalse();
        assertThat(verified).containsExactly("{argon2-p1}leurre");
    }

    @Test
    @DisplayName("AUTH-11, ADR 0026 : un mot de passe à demi-paire isolée n'est jamais haché (échec fermé)")
    void auth11LoneSurrogateIsNeverHashed() {
        Password malformed = Password.fromInput(Character.toString(0xD800) + PASSWORD);

        assertThatThrownBy(() -> hasher.hash(malformed)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AUTH-13 : deux hachés du même mot de passe diffèrent par leur sel")
    void auth13EachHashHasItsOwnSalt() {
        Password password = Password.fromInput("fjord-tuque-raquette");

        assertThat(hasher.hash(password)).isNotEqualTo(hasher.hash(password));
    }

    @Test
    @DisplayName("AUTH-11 : la vérification accepte la forme équivalente en NFKC et refuse tout autre mot de passe")
    void auth11MatchesTheNfkcEquivalent() {
        String encoded = hasher.hash(Password.fromInput("ﬁord-tuque-raquette"));

        assertThat(hasher.matches(Password.fromInput("fiord-tuque-raquette"), encoded))
                .isTrue();
        assertThat(hasher.matches(Password.fromInput("fiord-tuque-raquettE"), encoded))
                .isFalse();
    }

    @Test
    @DisplayName("AUTH-11 : un mot de passe à demi-paire isolée ne correspond à aucun haché, sans erreur (échec fermé)")
    void auth11LoneSurrogateMatchesNothing() {
        String encoded = hasher.hash(Password.fromInput("fjord-tuque-raquette"));

        assertThat(hasher.matches(Password.fromInput(Character.toString(0xD800) + "fjord-tuque-raquette"), encoded))
                .isFalse();
    }

    @Test
    @DisplayName("AUTH-13 : au plus N calculs Argon2id simultanés ; le suivant attend un permis (A06, ADR 0019)")
    void a06ConcurrentHashesAreBounded() throws Exception {
        int permits = 2;
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch saturated = new CountDownLatch(permits);
        PasswordEncoder blocking = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                saturated.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                running.decrementAndGet();
                return "{argon2-p1}x";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return false;
            }
        };
        Argon2PasswordHasher bounded = new Argon2PasswordHasher(PEPPER, blocking, permits);
        ExecutorService pool = Executors.newFixedThreadPool(permits + 1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i <= permits; i++) {
                results.add(pool.submit(() -> bounded.hash(Password.fromInput("fjord-tuque-raquette"))));
            }
            assertThat(saturated.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(running.get()).as("le calcul en trop attend").isEqualTo(permits);

            release.countDown();
            for (Future<String> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("{argon2-p1}x");
            }
            assertThat(maxRunning.get()).isEqualTo(permits);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AUTH-13 : le nombre de permis vaut le nombre de processeurs, au moins 2 (A06)")
    void a06PermitsFollowTheProcessors() {
        assertThat(Argon2PasswordHasher.defaultPermits())
                .isEqualTo(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    /** Sel de 18 octets et sortie de 32 octets, en base64 sans remplissage : une forme valide. */
    private static final String SALT = "c2Vsc2Vsc2Vsc2Vsc2Vsc2Vs";

    private static final String OUTPUT = "A".repeat(43);

    // Tailles en base64 sans remplissage : 16, 15, 64 et 65 octets de sel ou de sortie, 31 octets de sortie.
    private static final String SALT16 = "c2Vsc2Vsc2Vsc2Vsc2Vsc2";
    private static final String SALT15 = "AAAAAAAAAAAAAAAAAAAA";
    private static final String BYTES64 =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String BYTES65 =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OUTPUT31 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String OUTPUT_LITERAL = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{argon2-p1}$argon2id$v=19$m=65536,t=2,p=1$",
                "{argon2-p1}$argon2id$v=19$m=19456,t=10,p=1$",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=4$"
            })
    @DisplayName("ADR 0026, point 7 : un haché aux bornes hautes est vérifié pour lui-même, pas par le leurre")
    void adr0026UpperBoundsAreVerified(String prefix) {
        for (String stored : new String[] {prefix + SALT16 + "$" + OUTPUT_LITERAL, prefix + BYTES64 + "$" + BYTES64}) {
            List<String> verified = new ArrayList<>();
            PasswordEncoder recording = new PasswordEncoder() {
                @Override
                public String encode(CharSequence raw) {
                    return "{argon2-p1}leurre";
                }

                @Override
                public boolean matches(CharSequence raw, String encoded) {
                    verified.add(encoded);
                    return true;
                }
            };

            assertThat(new Argon2PasswordHasher(PEPPER, recording, 2).matches(Password.fromInput(PASSWORD), stored))
                    .isTrue();
            assertThat(verified).containsExactly(stored);
        }
    }

    @Test
    @DisplayName("ADR 0026, point 7 : un haché aux paramètres durcis, dans les bornes, reste vérifiable")
    void adr0026HardenedParametersStillVerify() {
        String peppered = encoded(Digests.hmacSha256(PEPPER, PASSWORD.getBytes(StandardCharsets.UTF_8)));
        String hardened = "{argon2-p1}" + new Argon2PasswordEncoder(16, 32, 1, 32_768, 3).encode(peppered);

        assertThat(hasher.matches(Password.fromInput(PASSWORD), hardened)).isTrue();
        assertThat(hasher.recognises(hardened))
                .as("seuls les paramètres d'aujourd'hui sont enregistrés")
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT + "$AA",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$!!!!$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$c2Vs$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "{argon2-p1}$argon2id$v=19$m=4194304,t=2,p=1$" + SALT + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=4096,t=2,p=1$" + SALT + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=1,p=1$" + SALT + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=11,p=1$" + SALT + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=0$" + SALT + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=5$" + SALT + "$" + "A",
                "{argon2-p1}$argon2i$v=19$m=19456,t=2,p=1$" + SALT + "$" + "A",
                "{argon2-p1}$argon2id$v=16$m=19456,t=2,p=1$" + SALT + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT + "$" + "A" + "$x",
                "{argon2-p1}",
                // Ancrage : une forme valide précédée ou suivie d'autre chose n'est pas un haché.
                "x{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT16 + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT16 + "$" + OUTPUT_LITERAL + "$x",
                // Bornes de l'ADR 0026, point 7, franchies d'une unité.
                "{argon2-p1}$argon2id$v=19$m=19455,t=2,p=1$" + SALT16 + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=65537,t=2,p=1$" + SALT16 + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT15 + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + BYTES65 + "$" + "A",
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT16 + "$" + OUTPUT31,
                "{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT16 + "$" + BYTES65
            })
    @DisplayName("AUTH-21, ADR 0026 (point 7) : un haché corrompu ou hors bornes coûte une vérification, sur le leurre")
    void auth21MalformedHashesCostOneDecoyVerification(String stored) {
        String malformed = stored.endsWith("$A") ? stored.substring(0, stored.length() - 1) + OUTPUT : stored;
        List<String> verified = new ArrayList<>();
        PasswordEncoder recording = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "{argon2-p1}leurre";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                verified.add(encoded);
                return true;
            }
        };
        Argon2PasswordHasher measured = new Argon2PasswordHasher(PEPPER, recording, 2);

        assertThat(measured.matches(Password.fromInput(PASSWORD), malformed)).isFalse();
        assertThat(verified).containsExactly("{argon2-p1}leurre");
    }

    @Test
    @DisplayName("AUTH-13, ADR 0026 : seul un haché aux paramètres d'aujourd'hui, sel de 16 octets et sortie de 32, est"
            + " enregistrable")
    void auth13RecognisesChecksTheSaltAndOutputSizes() {
        assertThat(hasher.recognises("{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$c2Vs$" + OUTPUT))
                .isFalse();
        assertThat(hasher.recognises("{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT + "$AA"))
                .isFalse();
        assertThat(hasher.recognises("{argon2-p1}$argon2id$v=19$m=19456,t=2,p=1$" + SALT.substring(2) + "$" + OUTPUT))
                .isTrue();
    }
}
