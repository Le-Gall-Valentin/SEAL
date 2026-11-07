package fr.vlegall.archfixture.alpha.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;

/** Interdit : seul le contexte authentication manipule les mots de passe. */
public class PasswordAdapter {
    boolean check(PasswordEncoder encoder, String raw, String hash) {
        return encoder.matches(raw, hash);
    }
}
