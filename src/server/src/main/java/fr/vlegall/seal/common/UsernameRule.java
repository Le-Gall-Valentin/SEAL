package fr.vlegall.seal.common;

/**
 * AUTH-01 : forme d'un identifiant, partagée par {@code identity}, qui la garde à l'inscription, et par
 * {@code friends}, qui vérifie la saisie d'une invitation (ADR 0037, point 6). La règle n'est écrite qu'une fois, comme
 * {@link DisplayNameRule}.
 */
public final class UsernameRule {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 20;

    private UsernameRule() {}

    /**
     * {@code ^(?=.{3,20}$)[a-z0-9]+(?:[._][a-z0-9]+)*$} : 3 à 20 caractères, minuscules et chiffres, {@code .} et
     * {@code _} isolés entre deux alphanumériques. Vérification linéaire, sans expression régulière ;
     * {@code UsernameTest} la compare à l'expression du CDC.
     */
    public static boolean isWellFormed(String value) {
        int length = value.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        boolean previousWasSeparator = true;
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            boolean separator = c == '.' || c == '_';
            if (separator ? previousWasSeparator : !((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
            previousWasSeparator = separator;
        }
        return !previousWasSeparator;
    }
}
