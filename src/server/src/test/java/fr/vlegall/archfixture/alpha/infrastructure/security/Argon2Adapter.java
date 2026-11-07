package fr.vlegall.archfixture.alpha.infrastructure.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Interdit : utilise directement l'encodeur Argon2id (AUTH-13) hors du contexte authentication. */
public class Argon2Adapter {
    String hash(String raw) {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(raw);
    }
}
