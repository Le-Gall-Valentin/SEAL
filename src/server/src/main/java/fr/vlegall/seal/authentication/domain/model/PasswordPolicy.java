package fr.vlegall.seal.authentication.domain.model;

import fr.vlegall.seal.authentication.domain.port.out.Blocklist;
import fr.vlegall.seal.common.Unicode;
import java.util.List;
import java.util.stream.Stream;

/**
 * Politique NIST SP 800-63B-4 d'AUTH-11, dans la forme que fixe l'ADR 0019 (point 2) et que décrit PROTOCOL.md
 * (section 3) : le client applique la même.
 */
public final class PasswordPolicy {

    /** AUTH-11, en points de code après NFKC. */
    public static final int MIN_LENGTH = 15;

    public static final int MAX_LENGTH = 128;

    /** AUTH-11 : mots propres au service, qui ne comptent pas. */
    static final List<String> SERVICE_WORDS = List.of("seal", "uqac");

    /** Un mot du contexte plus court est ignoré : une partie locale d'une lettre retirerait cette lettre partout. */
    private static final int MIN_WORD_LENGTH = 3;

    private PasswordPolicy() {}

    /**
     * Vrai si le mot de passe respecte AUTH-11 : 15 à 128 points de code, absent de la liste de blocage, et 15 points
     * de code au moins une fois retirés les mots du contexte.
     *
     * @param personalWords identifiant et partie locale du courriel, avant {@code +} et {@code @}
     */
    public static boolean accepts(Password password, List<String> personalWords, Blocklist blocklist) {
        String value = password.value();
        if (!hasLengthBetween(value, MIN_LENGTH, MAX_LENGTH)) {
            return false;
        }
        String folded = fold(value);
        return !blocklist.contains(folded)
                && hasLengthBetween(withoutContextWords(folded, personalWords), MIN_LENGTH, Integer.MAX_VALUE);
    }

    /** Forme comparée : NFKC puis minuscules, pour la saisie comme pour la liste de blocage et les mots du contexte. */
    public static String fold(String text) {
        return Unicode.lowerCase(Unicode.nfkc(text));
    }

    /** Retire chaque occurrence de chaque mot, jusqu'à ce qu'il n'en reste aucune (« sesealal » ne laisse rien). */
    private static String withoutContextWords(String folded, List<String> personalWords) {
        List<String> words = Stream.concat(personalWords.stream(), SERVICE_WORDS.stream())
                .map(PasswordPolicy::fold)
                .filter(word -> hasLengthBetween(word, MIN_WORD_LENGTH, Integer.MAX_VALUE))
                .distinct()
                .toList();
        String remaining = folded;
        boolean removed;
        do {
            removed = false;
            for (String word : words) {
                String shorter = remaining.replace(word, "");
                if (shorter.length() < remaining.length()) {
                    remaining = shorter;
                    removed = true;
                }
            }
        } while (removed);
        return remaining;
    }

    private static boolean hasLengthBetween(String text, int min, int max) {
        int length = text.codePointCount(0, text.length());
        return length >= min && length <= max;
    }
}
