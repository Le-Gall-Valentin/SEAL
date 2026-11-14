package fr.vlegall.seal.keys.domain.port.out;

/** Source d'octets aléatoires : défis de preuve de possession (KEY-03). */
@FunctionalInterface
public interface RandomBytes {

    /** {@code count} octets tirés par un générateur cryptographique. */
    byte[] next(int count);
}
