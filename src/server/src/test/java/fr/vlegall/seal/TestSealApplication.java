package fr.vlegall.seal;

import fr.vlegall.seal.common.PasswordPepper;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.boot.SpringApplication;

/**
 * Lance SEAL en développement avec un PostgreSQL jetable : {@code ./mvnw spring-boot:test-run}, depuis {@code src/server}.
 * Sans {@code SEAL_PASSWORD_PEPPER}, un poivre aléatoire sert le temps du lancement (ADR 0026, point 8).
 * Le profil demo s'applique, donc TLS : les certificats de {@code src/certs} doivent exister ({@code scripts/gen-certs.sh}).
 */
public class TestSealApplication {

    /** ADR 0026, point 8 : variable lue par {@code seal.passwords.pepper}. */
    static final String PEPPER_VARIABLE = "SEAL_PASSWORD_PEPPER";

    public static void main(String[] args) {
        if (!pepperGiven(args)) {
            // La base Testcontainers est jetable : un poivre jetable ne perd rien. Docker et la production restent en
            // échec fermé ; la valeur n'est jamais affichée (SEC-21).
            System.setProperty(PEPPER_VARIABLE, randomPepper());
            System.err.println("SEAL en développement : poivre des mots de passe aléatoire, propre à ce lancement"
                    + " (ADR 0026, point 8)");
        }
        SpringApplication.from(SealApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }

    /** Vrai si un poivre est déjà donné : par l'environnement, une propriété système ou la ligne de commande. */
    static boolean pepperGiven(String[] args) {
        String fromEnvironment = System.getenv(PEPPER_VARIABLE);
        return (fromEnvironment != null && !fromEnvironment.isBlank())
                || System.getProperty(PEPPER_VARIABLE) != null
                || Arrays.stream(args).anyMatch(arg -> arg.startsWith("--seal.passwords.pepper="));
    }

    /** 32 octets aléatoires en base64 (ADR 0026, point 8). */
    static String randomPepper() {
        byte[] pepper = new byte[PasswordPepper.BYTES];
        new SecureRandom().nextBytes(pepper);
        return Base64.getEncoder().encodeToString(pepper);
    }
}
