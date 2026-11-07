package fr.vlegall.seal.security.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AUTH-60 : le client recharge son jeton CSRF au démarrage, après la connexion et après la déconnexion. Lire le jeton
 * suffit à déposer le cookie {@code XSRF-TOKEN}, que le client renvoie dans l'en-tête {@code X-XSRF-TOKEN}.
 */
@RestController
class CsrfController {

    @GetMapping("/api/v1/csrf")
    ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }
}
