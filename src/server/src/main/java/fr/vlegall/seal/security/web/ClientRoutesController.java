package fr.vlegall.seal.security.web;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * TRN-02, ADR 0024 (point 5) : les adresses des écrans du client, ouvertes directement ou rechargées, renvoient le
 * client compilé, sur l'origine unique. C'est une liste fermée, lue par GET : toute autre adresse garde son
 * comportement.
 */
@Controller
public class ClientRoutesController {

    public static final String LOGIN = "/connexion";
    private static final String REGISTER = "/inscription";
    public static final String VERIFY_EMAIL = "/verification-courriel";
    private static final String FORGOT_PASSWORD = "/mot-de-passe/oublie";
    // AUTH-70, ADR 0022 : adresse du lien envoyé par courriel.
    private static final String RESET_PASSWORD = "/mot-de-passe/nouveau";
    /** AUTH-32, ADR 0032 (point 4) : choix de l'identifiant après un premier passage OAuth (Identifiant-OAuth). */
    public static final String CHOOSE_USERNAME = "/inscription/identifiant";

    /** ADR 0034 (point 2) : écran Paramètres, où revient une ré-authentification OAuth (AUTH-55). */
    public static final String SETTINGS = "/parametres";
    /** ADR 0038 (point 5) : écran Amis ; l'onglet Invitations est un paramètre de requête. */
    private static final String FRIENDS = "/amis";
    /** ADR 0032 (point 2) : retours vers l'écran de connexion, avec un code de la section 14.6 du CDC. */
    public static final String LOGIN_REQUEST_REJECTED = LOGIN + "?erreur=REQUEST_REJECTED";

    public static final String LOGIN_RATE_LIMITED = LOGIN + "?erreur=RATE_LIMITED";

    public static final String LOGIN_OAUTH_EMAIL_IN_USE = LOGIN + "?erreur=OAUTH_EMAIL_IN_USE";
    /** ADR 0034 (point 2) : retours d'une ré-authentification refusée vers les paramètres, avec les mêmes codes. */
    public static final String SETTINGS_REQUEST_REJECTED = SETTINGS + "?erreur=REQUEST_REJECTED";

    public static final String SETTINGS_RATE_LIMITED = SETTINGS + "?erreur=RATE_LIMITED";

    /** Adresses ouvertes à tous par {@code SecurityConfig}, comme {@code /} : du HTML statique, sans donnée. */
    public static final List<String> PATHS =
            List.of(LOGIN, REGISTER, VERIFY_EMAIL, FORGOT_PASSWORD, RESET_PASSWORD, CHOOSE_USERNAME, SETTINGS, FRIENDS);

    /**
     * ADR 0032 (point 2) et ADR 0034 (point 2) : adresse d'un départ ou d'un retour OAuth refusé, aux paramètres pour
     * une ré-authentification, à la connexion sinon. Toujours une constante (A01, redirection ouverte).
     */
    public static String rejectedTo(boolean reauthentication) {
        return reauthentication ? SETTINGS_REQUEST_REJECTED : LOGIN_REQUEST_REJECTED;
    }

    /** Même choix pour un départ ou un retour OAuth au-delà de sa limite (SEC-10). */
    public static String rateLimitedTo(boolean reauthentication) {
        return reauthentication ? SETTINGS_RATE_LIMITED : LOGIN_RATE_LIMITED;
    }

    @GetMapping({LOGIN, REGISTER, VERIFY_EMAIL, FORGOT_PASSWORD, RESET_PASSWORD, CHOOSE_USERNAME, SETTINGS, FRIENDS})
    String client() {
        return "forward:/index.html";
    }
}
