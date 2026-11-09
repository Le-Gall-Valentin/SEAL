package fr.vlegall.seal.identity.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;

/** Outils communs aux adaptateurs SQL d'{@code identity}. */
final class SqlSupport {

    private SqlSupport() {}

    /** Instant en UTC, pour une colonne {@code timestamptz} (ISO 8601 UTC partout, CDC section 14). */
    static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * Une écriture qui devait toucher une ligne exactement : sinon, l'état n'est pas celui que l'appelant a vérifié sous
     * verrou, et la transaction échoue plutôt que de passer pour un succès (échec fermé, A10).
     */
    static void requireOneRow(int rows, String operation) {
        if (rows != 1) {
            throw new IllegalStateException(operation + " : " + rows + " ligne(s) modifiée(s) au lieu d'une");
        }
    }

    /**
     * Vrai si PostgreSQL nomme {@code constraint} dans le message de l'erreur, même sans le détail retiré par SEC-21. Le
     * nom est cherché comme un mot entier, quels que soient les guillemets de la langue du serveur (« » en français).
     */
    static boolean violates(DuplicateKeyException exception, String constraint) {
        String message = exception.getMostSpecificCause().getMessage();
        if (message == null) {
            return false;
        }
        for (int at = message.indexOf(constraint); at >= 0; at = message.indexOf(constraint, at + 1)) {
            int end = at + constraint.length();
            if (!isNamePart(message, at - 1) && !isNamePart(message, end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNamePart(String text, int index) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        char c = text.charAt(index);
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
