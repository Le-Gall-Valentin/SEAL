package fr.vlegall.archfixture.authentication.infrastructure;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;

/** Interdit, même dans authentication : seul Argon2PasswordEncoder se sert de Bouncy Castle (ADR 0018). */
public class BouncyCastleAdapter {
    Argon2BytesGenerator generator() {
        return new Argon2BytesGenerator();
    }
}
