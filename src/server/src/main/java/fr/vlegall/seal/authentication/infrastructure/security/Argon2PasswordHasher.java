package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.common.PasswordPepper;
import fr.vlegall.seal.common.Unicode;
import fr.vlegall.seal.crypto.Digests;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id par l'{@link Argon2PasswordEncoder} de Spring Security, dans un {@link DelegatingPasswordEncoder} pour
 * pouvoir durcir les paramètres plus tard (AUTH-13, ADR 0018). Le mot de passe est d'abord poivré : Argon2id porte sur
 * base64(HMAC-SHA256(poivre, mot de passe)), et le haché sur le préfixe {@code {argon2-p1}} (ADR 0026). Chaque calcul
 * occupe 19 Mio, 64 Mio au plus pour un haché durci dans les bornes de l'ADR 0026 : un sémaphore équitable en borne le
 * nombre simultané, sans délai d'abandon (ADR 0019, point 3).
 */
@Component
public final class Argon2PasswordHasher implements PasswordHasher {

    /** ADR 0026, point 3 : Argon2id avec le poivre n° 1 ; un autre poivre prendra un autre identifiant. */
    static final String ENCODING_ID = "argon2-p1";

    // AUTH-13 : minimum OWASP.
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    // ADR 0026, point 7 : bornes d'un haché vérifiable. Le plancher est celui d'AUTH-13 ; le plafond borne ce qu'une
    // ligne altérée de la base pourrait imposer en mémoire et en temps.
    private static final int MAX_MEMORY_KIB = 65_536;

    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_PARALLELISM = 4;
    private static final int MAX_SALT_OR_HASH_LENGTH = 64;

    /** {@code {argon2-p1}$argon2id$v=19$m=…,t=…,p=…$sel$sortie}, sel et sortie en base64 sans remplissage. */
    private static final Pattern FORM = Pattern.compile(Pattern.quote("{" + ENCODING_ID + "}$argon2id$v=19$m=")
            + "(\\d{1,9}),t=(\\d{1,9}),p=(\\d{1,9})\\$([A-Za-z0-9+/]{1,128})\\$([A-Za-z0-9+/]{1,128})");

    /** Mot de passe du haché leurre : il n'a rien de secret, son résultat n'est jamais lu (AUTH-21). */
    private static final String DECOY_PASSWORD = "leurre-de-temps-constant-AUTH-21";

    private final byte[] pepper;
    private final PasswordEncoder encoder;
    private final Semaphore permits;
    private volatile @Nullable String decoy;

    @Autowired
    public Argon2PasswordHasher(PasswordPepper pepper) {
        this(
                pepper.value(),
                new DelegatingPasswordEncoder(
                        ENCODING_ID,
                        Map.of(
                                ENCODING_ID,
                                new Argon2PasswordEncoder(
                                        SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS))),
                defaultPermits());
        // AUTH-21 : le leurre est prêt avant la première connexion, qui coûte alors une seule vérification.
        decoy();
    }

    Argon2PasswordHasher(byte[] pepper, PasswordEncoder encoder, int permits) {
        this.pepper = Objects.requireNonNull(pepper, "pepper").clone();
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.permits = new Semaphore(permits, true);
    }

    /** Un calcul par processeur, au moins 2 : la mémoire reste bornée à N × 64 Mio (N × 19 Mio aujourd'hui). */
    static int defaultPermits() {
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    @Override
    public String hash(Password password) {
        String peppered = peppered(password.value());
        return bounded(() -> Objects.requireNonNull(encoder.encode(peppered), "haché"));
    }

    /**
     * Une demi-paire isolée ne s'encode pas en UTF-8, et un haché non reconnu, comme celui d'avant le poivre, ne se
     * vérifie pas (ADR 0026, point 4) : ils ne correspondent à rien, sans erreur (échec fermé). Le calcul a quand même
     * lieu, sur le leurre, pour que la durée ne dise rien du compte (AUTH-21).
     */
    @Override
    public boolean matches(Password password, String encoded) {
        if (!Unicode.isWellFormed(password.value())
                || shape(encoded).filter(Shape::verifiable).isEmpty()) {
            String decoyHash = decoy();
            String decoyPeppered = peppered(DECOY_PASSWORD);
            bounded(() -> encoder.matches(decoyPeppered, decoyHash));
            return false;
        }
        String peppered = peppered(password.value());
        return bounded(() -> encoder.matches(peppered, encoded));
    }

    /** Haché neuf, aux paramètres exacts d'aujourd'hui (AUTH-13) : seul enregistrable. */
    @Override
    public boolean recognises(String encoded) {
        return shape(encoded).filter(Shape::current).isPresent();
    }

    @Override
    public void verifyDecoy(Password password) {
        matches(password, decoy());
    }

    /** Haché leurre, calculé une seule fois, au même coût qu'un vrai haché. */
    private String decoy() {
        String current = decoy;
        if (current == null) {
            synchronized (this) {
                current = decoy;
                if (current == null) {
                    current = hash(Password.fromInput(DECOY_PASSWORD));
                    decoy = current;
                }
            }
        }
        return current;
    }

    /**
     * ADR 0026, point 2 : base64(HMAC-SHA256(poivre, mot de passe en UTF-8)). Un mot de passe mal formé n'arrive
     * jamais jusqu'ici (AUTH-11) ; s'il y arrivait, il serait refusé plutôt qu'encodé avec un caractère de remplacement.
     */
    private String peppered(String password) {
        if (!Unicode.isWellFormed(password)) {
            throw new IllegalArgumentException("mot de passe mal formé : rien à hacher");
        }
        return Base64.getEncoder()
                .encodeToString(Digests.hmacSha256(pepper, password.getBytes(StandardCharsets.UTF_8)));
    }

    /** Forme d'un haché {@code {argon2-p1}} : ses paramètres et les tailles décodées de son sel et de sa sortie. */
    private static Optional<Shape> shape(String encoded) {
        Matcher form = FORM.matcher(encoded);
        if (!form.matches()) {
            return Optional.empty();
        }
        return Optional.of(new Shape(
                Integer.parseInt(form.group(1)),
                Integer.parseInt(form.group(2)),
                Integer.parseInt(form.group(3)),
                decodedLength(form.group(4)),
                decodedLength(form.group(5))));
    }

    /** Taille décodée d'un base64 sans remplissage, -1 s'il est illisible. */
    private static int decodedLength(String base64) {
        try {
            return Base64.getDecoder().decode(base64).length;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    private record Shape(int memory, int iterations, int parallelism, int saltLength, int hashLength) {

        /** ADR 0026, point 7 : dans les bornes, donc vérifiable, même après un durcissement des paramètres. */
        boolean verifiable() {
            return between(memory, MEMORY_KIB, MAX_MEMORY_KIB)
                    && between(iterations, ITERATIONS, MAX_ITERATIONS)
                    && between(parallelism, PARALLELISM, MAX_PARALLELISM)
                    && between(saltLength, SALT_LENGTH, MAX_SALT_OR_HASH_LENGTH)
                    && between(hashLength, HASH_LENGTH, MAX_SALT_OR_HASH_LENGTH);
        }

        boolean current() {
            return memory == MEMORY_KIB
                    && iterations == ITERATIONS
                    && parallelism == PARALLELISM
                    && saltLength == SALT_LENGTH
                    && hashLength == HASH_LENGTH;
        }

        private static boolean between(int value, int min, int max) {
            return value >= min && value <= max;
        }
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
