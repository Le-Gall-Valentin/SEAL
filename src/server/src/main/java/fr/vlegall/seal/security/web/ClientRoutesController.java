package fr.vlegall.seal.security.web;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ADR 0024, point 5 : les adresses des écrans du client, ouvertes directement ou rechargées, renvoient le client
 * compilé. C'est une liste fermée, lue par GET : toute autre adresse garde son comportement.
 */
@Controller
public class ClientRoutesController {

    private static final String LOGIN = "/connexion";
    private static final String REGISTER = "/inscription";
    private static final String VERIFY_EMAIL = "/verification-courriel";
    private static final String FORGOT_PASSWORD = "/mot-de-passe/oublie";
    // ADR 0022 : adresse du lien envoyé par courriel.
    private static final String RESET_PASSWORD = "/mot-de-passe/nouveau";

    /** Adresses ouvertes à tous par {@code SecurityConfig}, comme {@code /} : du HTML statique, sans donnée. */
    public static final List<String> PATHS = List.of(LOGIN, REGISTER, VERIFY_EMAIL, FORGOT_PASSWORD, RESET_PASSWORD);

    @GetMapping({LOGIN, REGISTER, VERIFY_EMAIL, FORGOT_PASSWORD, RESET_PASSWORD})
    String client() {
        return "forward:/index.html";
    }
}
