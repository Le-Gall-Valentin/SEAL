package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.common.DisplayNameRule;
import fr.vlegall.seal.common.EmailFormat;
import fr.vlegall.seal.common.Unicode;
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
 * @param displayName nom proposé à l'inscription : vide, ou conforme à la règle du nom affiché (ACC-03,
 *     {@link DisplayNameRule}). Ce n'est qu'une suggestion : le nom choisi est vérifié à la création.
 */
public record ExternalIdentity(
        OAuthProvider provider, String subject, String email, boolean emailVerified, String displayName) {

    /** Longueur maximale d'un identifiant stable. */
    public static final int SUBJECT_MAX_LENGTH = 255;

    /**
     * SEC-01, A06 : longueur, en caractères UTF-16, au-delà de laquelle un nom ou un courriel du fournisseur n'est pas
     * normalisé. La normalisation NFC coûte un temps quadratique sur une longue suite de marques combinantes, et la
     * réponse du fournisseur n'a pas de taille maximale. Aucun nom ni courriel légitime n'en approche.
     */
    static final int RAW_MAX_LENGTH = 1024;

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
        if (!displayName.isEmpty() && !DisplayNameRule.isValid(displayName)) {
            throw new IllegalArgumentException("nom proposé hors de la règle d'ACC-03");
        }
    }

    /** L'identité lue chez le fournisseur : courriel ramené en NFC, nom nettoyé. */
    public static ExternalIdentity of(
            OAuthProvider provider, String subject, String email, boolean emailVerified, @Nullable String name) {
        if (email.length() > RAW_MAX_LENGTH) {
            throw new IllegalArgumentException("courriel du fournisseur invalide");
        }
        return new ExternalIdentity(provider, subject, Unicode.nfc(email), emailVerified, suggestedName(name));
    }

    /**
     * ACC-03 : le nom du fournisseur, privé des caractères que la règle du nom affiché interdit (contrôle, séparateurs,
     * format dont la direction, usage privé ; ADR 0040), puis normalisé en NFC, sans espace autour et tronqué à 50
     * points de code. Le nettoyage précède la normalisation : retirer un caractère après coup pourrait rapprocher une
     * lettre et un accent, et le nom ne serait plus en NFC. Un nom qui ne respecte pas la règle donne un nom vide : ce n'est qu'une proposition, que
     * l'utilisateur complète ; elle ne refuse jamais l'identité. Un nom de plus de {@link #RAW_MAX_LENGTH} caractères
     * n'est pas même lu.
     */
    static String suggestedName(@Nullable String name) {
        if (name == null || name.length() > RAW_MAX_LENGTH) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        name.codePoints().filter(c -> !DisplayNameRule.isForbidden(c)).forEach(kept::appendCodePoint);
        String normalized = Unicode.nfc(kept.toString()).strip();
        if (normalized.codePointCount(0, normalized.length()) > DisplayNameRule.MAX_LENGTH) {
            normalized = normalized
                    .substring(0, normalized.offsetByCodePoints(0, DisplayNameRule.MAX_LENGTH))
                    .strip();
        }
        return DisplayNameRule.isValid(normalized) ? normalized : "";
    }

    /** SEC-21 : le courriel n'apparaît pas dans les journaux. */
    @Override
    public String toString() {
        return "ExternalIdentity[provider=" + provider + ", subject=" + subject + ", email=***, emailVerified="
                + emailVerified + "]";
    }
}
