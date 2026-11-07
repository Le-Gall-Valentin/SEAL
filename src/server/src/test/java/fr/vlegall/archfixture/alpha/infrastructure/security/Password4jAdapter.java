package fr.vlegall.archfixture.alpha.infrastructure.security;

import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;

/** Interdit : un encodeur de la famille password4j hors du contexte authentication. */
public class Password4jAdapter {
    String hash(Argon2Password4jPasswordEncoder encoder, String raw) {
        return encoder.encode(raw);
    }
}
