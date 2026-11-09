package fr.vlegall.seal.identity.domain.model;

import fr.vlegall.seal.common.Unicode;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import java.util.ArrayList;
import java.util.List;

/**
 * Suggestion d'identifiant (AUTH-04). Le client applique le même algorithme au courriel saisi ; le serveur calcule les
 * suggestions de {@code username-availability} (AUTH-05, ADR 0019).
 */
public final class UsernameSuggestions {

    /** Étape 4 : base de repli quand il reste moins de 3 caractères. */
    static final String FALLBACK = "user";

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 20;
    private static final int FIRST_SUFFIX = 2;
    private static final int LAST_SUFFIX = 99;
    private static final int RANDOM_SUFFIX_LENGTH = 4;
    private static final char SEPARATOR = '.';

    private UsernameSuggestions() {}

    /** Étapes 1 à 4 : partie locale du courriel, avant {@code +} et {@code @}, puis {@link #baseFrom(String)}. */
    public static String baseFromEmail(EmailAddress email) {
        return baseFrom(email.localPart());
    }

    /** Étapes 2 à 4 ; le résultat est toujours un identifiant valide. */
    public static String baseFrom(String text) {
        String folded = Unicode.foldWithoutMarks(text);
        StringBuilder base = new StringBuilder(folded.length());
        boolean pendingSeparator = false;
        for (int i = 0; i < folded.length(); i++) {
            char c = folded.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (pendingSeparator && !base.isEmpty()) {
                    base.append(SEPARATOR);
                }
                pendingSeparator = false;
                base.append(c);
            } else {
                pendingSeparator = true;
            }
        }
        String truncated = truncate(base.toString(), MAX_LENGTH);
        return truncated.length() < MIN_LENGTH ? FALLBACK : truncated;
    }

    /** Étape 5 : suffixes 2 à 99, dans l'ordre, la base étant tronquée pour tenir dans 20 caractères. */
    public static List<Username> suffixed(String base) {
        List<Username> candidates = new ArrayList<>(LAST_SUFFIX - FIRST_SUFFIX + 1);
        for (int suffix = FIRST_SUFFIX; suffix <= LAST_SUFFIX; suffix++) {
            String digits = Integer.toString(suffix);
            candidates.add(new Username(truncate(base, MAX_LENGTH - digits.length()) + digits));
        }
        return candidates;
    }

    /** Étape 5, en dernier recours : 4 chiffres aléatoires après la base tronquée. */
    public static Username randomSuffixed(String base, RandomDigits random) {
        return new Username(truncate(base, MAX_LENGTH - RANDOM_SUFFIX_LENGTH) + random.next(RANDOM_SUFFIX_LENGTH));
    }

    /** Coupe à {@code max} caractères, puis retire les séparateurs de fin. */
    private static String truncate(String value, int max) {
        String cut = value.length() > max ? value.substring(0, max) : value;
        int end = cut.length();
        while (end > 0 && cut.charAt(end - 1) == SEPARATOR) {
            end--;
        }
        return cut.substring(0, end);
    }
}
