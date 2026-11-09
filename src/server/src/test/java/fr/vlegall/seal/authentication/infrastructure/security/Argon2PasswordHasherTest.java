package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.Password;
import java.util.ArrayList;
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
import org.springframework.security.crypto.password.PasswordEncoder;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    @Test
    @DisplayName("AUTH-13 : Argon2id, m = 19456, t = 2, p = 1, sel de 16 octets et sortie de 32, préfixé par {argon2}")
    void auth13HashUsesTheCdcParameters() {
        String encoded = hasher.hash(Password.fromInput("fjord-tuque-raquette"));

        assertThat(encoded).startsWith("{argon2}$argon2id$v=19$m=19456,t=2,p=1$");
        String[] parts = encoded.split("\\$");
        assertThat(Base64.getDecoder().decode(parts[4])).hasSize(16);
        assertThat(Base64.getDecoder().decode(parts[5])).hasSize(32);
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
    @DisplayName("A06 : au plus N calculs Argon2id simultanés ; le suivant attend un permis (ADR 0019)")
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
                return "{argon2}x";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return false;
            }
        };
        Argon2PasswordHasher bounded = new Argon2PasswordHasher(blocking, permits);
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
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("{argon2}x");
            }
            assertThat(maxRunning.get()).isEqualTo(permits);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("A06 : le nombre de permis vaut le nombre de processeurs, au moins 2")
    void a06PermitsFollowTheProcessors() {
        assertThat(Argon2PasswordHasher.defaultPermits())
                .isEqualTo(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }
}
