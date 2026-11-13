package fr.vlegall.seal.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * AUTH-16 vérifié sur toutes les routes, y compris celles à venir (d'après {@code WebAuthorizationConventionsTest} de
 * NIDO) : une session restreinte reçoit 403 partout, sauf sur la liste fermée d'AUTH-16.
 */
@SealIntegrationTest
class WebAuthorizationConventionsIT {

    /** AUTH-16 : seules routes permises à la session restreinte. */
    private static final Set<String> RESTRICTED_SESSION_ROUTES = Set.of(
            "GET /api/v1/csrf",
            "GET /api/v1/auth/me",
            "POST /api/v1/auth/verify-email",
            "POST /api/v1/auth/verify-email/resend",
            "POST /api/v1/auth/verify-email/change",
            "POST /api/v1/auth/logout");

    /**
     * ADR 0024, point 5 : pages du client, du HTML statique sans donnée, servies à toute session comme {@code /} ; la
     * session restreinte doit pouvoir ouvrir l'écran de vérification.
     */
    private static final Set<String> CLIENT_PAGES = Set.of(
            "GET /connexion",
            "GET /inscription",
            "GET /verification-courriel",
            "GET /mot-de-passe/oublie",
            "GET /mot-de-passe/nouveau");

    /** AUTH-53 : la déconnexion ferme la session ; essayée en dernier, elle ne fausse pas les autres routes. */
    private static final String LOGOUT = "POST /api/v1/auth/logout";

    /** Dispatch d'erreur du conteneur, sans donnée ni effet (ADR 0011). */
    private static final String ERROR_PATH = "/error";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @AfterEach
    void resetMail() {
        mail.reset();
    }

    /** Chaque route déclarée, « MÉTHODE /chemin », les variables de chemin remplacées par un UUID. */
    private List<String> routes() {
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            Set<RequestMethod> tried = methods.isEmpty()
                    ? Set.of(
                            RequestMethod.GET,
                            RequestMethod.POST,
                            RequestMethod.PUT,
                            RequestMethod.PATCH,
                            RequestMethod.DELETE)
                    : methods;
            for (String pattern : info.getPatternValues()) {
                if (pattern.equals(ERROR_PATH)) {
                    continue;
                }
                String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
                tried.forEach(method -> routes.add(method.name() + " " + path));
            }
        }
        List<String> ordered = new ArrayList<>(routes);
        if (ordered.remove(LOGOUT)) {
            ordered.add(LOGOUT);
        }
        return ordered;
    }

    @Test
    @DisplayName("AUTH-16 : une session restreinte reçoit 403 sur toute route hors de la liste d'AUTH-16")
    void auth16RestrictedSessionIsForbiddenEverywhereElse() throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        Cookie restricted =
                client.register("convention@exemple.ca", "convention").session();
        List<String> routes = routes();
        List<String> wrong = new ArrayList<>();

        for (String route : routes) {
            String[] parts = route.split(" ", 2);
            int status = mvc.perform(
                            client.unsafe(request(HttpMethod.valueOf(parts[0]), parts[1]), restricted, Map.of()))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            boolean page = CLIENT_PAGES.contains(route);
            boolean permitted = RESTRICTED_SESSION_ROUTES.contains(route);
            if (page ? status != 200 : permitted ? status == 401 || status == 403 : status != 403) {
                wrong.add(route + " → " + status);
            }
        }

        assertThat(wrong)
                .as("routes dont la réponse à une session restreinte contredit AUTH-16")
                .isEmpty();
        assertThat(routes)
                .as("la convention a bien regardé les routes de l'API")
                .hasSizeGreaterThanOrEqualTo(6);
    }
}
