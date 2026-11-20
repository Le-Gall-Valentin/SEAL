package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.EmailFormat;
import fr.vlegall.seal.common.Unicode;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Identité rendue par un fournisseur OAuth (section 5.4 du CDC). SEAL la retrouve par {@code (provider, subject)},
 * jamais par le courriel (AUTH-31).
 *
 * @param subject identifiant stable : {@code sub} de Google, {@code id} de GitHub en décimal ; ASCII imprimable, sans
 *     espace, 255 caractères au plus
 * @param email courriel du fournisseur, en NFC, au format d'{@link EmailFormat}
 * @param emailVerified vrai seulement si le fournisseur le garantit, selon la règle de la section 5.4
 * @param displayName nom proposé à l'inscription : sans caractère de contrôle, de format ni de direction, 50 points de
 *     code au plus (ACC-03), peut-être vide. Ce n'est qu'une suggestion : le nom choisi est vérifié à la création.
 */
public record ExternalIdentity(
        OAuthProvider provider, String subject, String email, boolean emailVerified, String displayName)
        implements Serializable {

    /** Gardée dans la session le temps de l'inscription en attente (AUTH-32), donc sérialisée par Spring Session. */
    @Serial
    private static final long serialVersionUID = 1L;

    /** Longueur maximale d'un identifiant stable. */
    public static final int SUBJECT_MAX_LENGTH = 255;

    /** ACC-03, en points de code. */
    public static final int DISPLAY_NAME_MAX_LENGTH = 50;

    public ExternalIdentity {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(displayName, "displayName");
        if (subject.isEmpty()
                || subject.length() > SUBJECT_MAX_LENGTH
                || subject.chars().anyMatch(c -> c < 0x21 || c > 0x7E)) {
            throw new IllegalArgumentException("identifiant de fournisseur invalide");
        }
        // Message fixe : le courriel n'est jamais recopié (SEC-21).
        if (!EmailFormat.isValid(email)) {
            throw new IllegalArgumentException("courriel du fournisseur invalide");
        }
        if (!displayName.equals(suggestedName(displayName))) {
            throw new IllegalArgumentException("nom proposé non nettoyé");
        }
    }

    /** L'identité lue chez le fournisseur : courriel ramené en NFC, nom nettoyé. */
    public static ExternalIdentity of(
            OAuthProvider provider, String subject, String email, boolean emailVerified, @Nullable String name) {
        return new ExternalIdentity(provider, subject, Unicode.nfc(email), emailVerified, suggestedName(name));
    }

    /**
     * ACC-03 : nom en NFC, sans caractère de contrôle, de format (dont ceux de direction), de séparation de ligne, d'usage
     * privé, ni point non attribué, tronqué à {@value #DISPLAY_NAME_MAX_LENGTH} points de code, sans espace autour.
     */
    static String suggestedName(@Nullable String name) {
        if (name == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        Unicode.nfc(name).codePoints().filter(ExternalIdentity::kept).forEach(kept::appendCodePoint);
        String stripped = kept.toString().strip();
        if (stripped.codePointCount(0, stripped.length()) > DISPLAY_NAME_MAX_LENGTH) {
            stripped = stripped.substring(0, stripped.offsetByCodePoints(0, DISPLAY_NAME_MAX_LENGTH));
        }
        return stripped.strip();
    }

    private static boolean kept(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SURROGATE,
                    Character.PRIVATE_USE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR -> false;
            default -> true;
        };
    }

    /** SEC-21 : le courriel n'apparaît pas dans les journaux. */
    @Override
    public String toString() {
        return "ExternalIdentity[provider=" + provider + ", subject=" + subject + ", email=***, emailVerified="
                + emailVerified + "]";
    }
}
