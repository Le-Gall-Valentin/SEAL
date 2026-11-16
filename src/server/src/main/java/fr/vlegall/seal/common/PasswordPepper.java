package fr.vlegall.seal.common;

import java.util.Base64;
import java.util.Objects;
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
        Objects.requireNonNull(pepper, "seal.passwords.pepper est obligatoire : remplissez SEAL_PASSWORD_PEPPER");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(pepper);
        } catch (IllegalArgumentException e) {
            // Sans cause chaînée : le message de java.util.Base64 cite le caractère fautif, un morceau du secret
            // (SEC-21).
            throw new IllegalArgumentException("seal.passwords.pepper doit valoir " + BYTES + " octets en base64");
        }
        if (decoded.length != BYTES) {
            throw new IllegalArgumentException("seal.passwords.pepper doit valoir " + BYTES + " octets en base64");
        }
        this.value = decoded;
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
