package fr.vlegall.seal.authentication.domain.port.out;

/** Source d'octets aléatoires : jetons de réinitialisation (AUTH-70). */
@FunctionalInterface
public interface RandomBytes {

    /** {@code count} octets tirés par un générateur cryptographique. */
    byte[] next(int count);
}
