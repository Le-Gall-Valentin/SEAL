package fr.vlegall.archfixture.authentication.infrastructure;

import org.springframework.security.crypto.password.PasswordEncoder;

/** Permis : le contexte authentication manipule les mots de passe. */
public class HashingAdapter {
    boolean check(PasswordEncoder encoder, String raw, String hash) {
        return encoder.matches(raw, hash);
    }
}
