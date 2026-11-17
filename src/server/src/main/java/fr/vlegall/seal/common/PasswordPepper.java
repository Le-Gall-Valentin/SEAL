package fr.vlegall.seal.common;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AUTH-13, ADR 0026 : poivre des mots de passe, commun à tout le serveur. 32 octets en base64, lus dans
 * {@code SEAL_PASSWORD_PEPPER} ({@code seal.passwords.pepper}) et jamais écrits en base (SEC-22). Un poivre absent,
 * illisible ou d'une autre taille arrête le démarrage ; sa valeur n'apparaît dans aucun message ni {@code toString}
 * (SEC-21). Distinct de {@link SealProperties} : seul le hacheur des mots de passe le reçoit.
 */
@ConfigurationProperties("seal.passwords")
public final class PasswordPepper {

    /** ADR 0026 : 256 bits, la taille de la sortie de HMAC-SHA256. */
    public static final int BYTES = 32;

    private final byte[] value;

    /** @param pepper 32 octets en base64 */
    public PasswordPepper(String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalArgumentException(
                    "SEAL_PASSWORD_PEPPER est vide : lancez scripts/gen-certs.sh (seal.passwords.pepper, ADR 0026)");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(pepper);
        } catch (IllegalArgumentException e) {
            // Sans cause chaînée : le message de java.util.Base64 cite le caractère fautif, un morceau du secret
            // (SEC-21).
            throw invalid();
        }
        if (decoded.length != BYTES) {
            throw invalid();
        }
        this.value = decoded;
    }

    /** Message fixe, sans la valeur reçue (SEC-21). */
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "SEAL_PASSWORD_PEPPER (seal.passwords.pepper) doit valoir " + BYTES + " octets en base64");
    }

    /** Une copie : l'appelant ne peut pas modifier le poivre. */
    public byte[] value() {
        return value.clone();
    }

    @Override
    public String toString() {
        return "PasswordPepper[***]";
    }
}
