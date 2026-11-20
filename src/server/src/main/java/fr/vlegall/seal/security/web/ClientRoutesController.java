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
    private static final String VERIFY_EMAIL = "/verification-courriel";
    private static final String FORGOT_PASSWORD = "/mot-de-passe/oublie";
    // AUTH-70, ADR 0022 : adresse du lien envoyé par courriel.
    private static final String RESET_PASSWORD = "/mot-de-passe/nouveau";
    /** AUTH-32, ADR 0032 (point 4) : choix de l'identifiant après un premier passage OAuth (Identifiant-OAuth). */
    private static final String CHOOSE_USERNAME = "/inscription/identifiant";

    /** Adresses ouvertes à tous par {@code SecurityConfig}, comme {@code /} : du HTML statique, sans donnée. */
    public static final List<String> PATHS =
            List.of(LOGIN, REGISTER, VERIFY_EMAIL, FORGOT_PASSWORD, RESET_PASSWORD, CHOOSE_USERNAME);

    @GetMapping({LOGIN, REGISTER, VERIFY_EMAIL, FORGOT_PASSWORD, RESET_PASSWORD, CHOOSE_USERNAME})
    String client() {
        return "forward:/index.html";
    }
}
