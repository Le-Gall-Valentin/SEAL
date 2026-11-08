package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id par l'{@link Argon2PasswordEncoder} de Spring Security, dans un {@link DelegatingPasswordEncoder} pour
 * pouvoir durcir les paramètres plus tard (AUTH-13, ADR 0018). Chaque calcul occupe 19 Mio : un sémaphore équitable en
 * borne le nombre simultané, sans délai d'abandon (ADR 0019, point 3).
 */
@Component
public final class Argon2PasswordHasher implements PasswordHasher {

    static final String ENCODING_ID = "argon2";

    // AUTH-13 : minimum OWASP.
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final PasswordEncoder encoder;
    private final Semaphore permits;

    public Argon2PasswordHasher() {
        this(
                new DelegatingPasswordEncoder(
                        ENCODING_ID,
                        Map.of(
                                ENCODING_ID,
                                new Argon2PasswordEncoder(
                                        SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS))),
                defaultPermits());
    }

    Argon2PasswordHasher(PasswordEncoder encoder, int permits) {
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.permits = new Semaphore(permits, true);
    }

    /** Un calcul par processeur, au moins 2 : la mémoire reste bornée à N × 19 Mio. */
    static int defaultPermits() {
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    @Override
    public String hash(Password password) {
        return bounded(() -> Objects.requireNonNull(encoder.encode(password.value()), "haché"));
    }

    @Override
    public boolean matches(Password password, String encoded) {
        return bounded(() -> encoder.matches(password.value(), encoded));
    }

    private <T> T bounded(Supplier<T> computation) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Échec fermé : sans permis, aucun calcul et aucune réponse positive.
            throw new IllegalStateException("calcul Argon2id interrompu", e);
        }
        try {
            return computation.get();
        } finally {
            permits.release();
        }
    }
}
