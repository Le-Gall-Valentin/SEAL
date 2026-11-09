package fr.vlegall.seal.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Transformations Unicode de SEAL, écrites une seule fois. Chaque appelant valide la valeur transformée, jamais la
 * saisie brute : c'est l'ordre que recommande la règle {@code IMPROPER_UNICODE} de FindSecBugs (normaliser d'abord,
 * valider ensuite), d'où son exclusion limitée à cette classe ({@code spotbugs-exclude.xml}).
 */
public final class Unicode {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private Unicode() {}

    /** SEC-01 : normalisation NFC des saisies (noms, courriels). */
    public static String nfc(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC);
    }

    public static boolean isNfc(String text) {
        return Normalizer.isNormalized(text, Normalizer.Form.NFC);
    }

    /**
     * Vrai si le texte est une suite de points de code valide, sans demi-paire isolée : Java et JavaScript
     * l'autorisent, mais UTF-8 ne sait pas l'encoder (AUTH-11, SEC-01).
     */
    public static boolean isWellFormed(String text) {
        return text.codePoints()
                .noneMatch(point -> point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE);
    }

    /** AUTH-11 : normalisation NFKC des mots de passe. */
    public static String nfkc(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC);
    }

    /** Minuscules par la correspondance par défaut d'Unicode, indépendante de la langue du serveur. */
    public static String lowerCase(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** AUTH-04, étape 2, dans l'ordre du CDC : NFKD, retrait des diacritiques, puis minuscules. */
    public static String foldWithoutMarks(String text) {
        return lowerCase(
                MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKD)).replaceAll(""));
    }
}
