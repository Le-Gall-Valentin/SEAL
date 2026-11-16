package fr.vlegall.seal.support;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;

/** Hachés d'avant le poivre (ADR 0026, point 4) : Argon2id aux paramètres d'AUTH-13, sous l'ancien préfixe {argon2}. */
public final class UnpepperedHashes {

    private UnpepperedHashes() {}

    public static String of(String password) {
        return new DelegatingPasswordEncoder(
                        "argon2", Map.of("argon2", new Argon2PasswordEncoder(16, 32, 1, 19_456, 2)))
                .encode(password);
    }
}
