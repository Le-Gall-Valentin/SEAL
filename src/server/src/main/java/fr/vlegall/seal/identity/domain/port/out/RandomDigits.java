package fr.vlegall.seal.identity.domain.port.out;

/** Source de chiffres aléatoires : codes de vérification (AUTH-14) et suffixes d'identifiant (AUTH-04). */
@FunctionalInterface
public interface RandomDigits {

    /** Chaîne de {@code length} chiffres ASCII, tirés par un générateur cryptographique. */
    String next(int length);
}
