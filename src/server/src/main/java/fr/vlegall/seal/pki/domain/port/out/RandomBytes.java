package fr.vlegall.seal.pki.domain.port.out;

/** Source d'octets aléatoires : numéros de série des certificats (KEY-04). */
@FunctionalInterface
public interface RandomBytes {

    /** {@code count} octets tirés par un générateur cryptographique. */
    byte[] next(int count);
}
