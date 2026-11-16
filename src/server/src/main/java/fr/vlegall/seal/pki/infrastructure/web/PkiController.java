package fr.vlegall.seal.pki.infrastructure.web;

import fr.vlegall.seal.pki.application.port.in.AuthorityQuery;
import fr.vlegall.seal.pki.infrastructure.web.dto.AuthorityView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Clé publique de l'Autorité, épinglée par le client au premier usage (KEY-10, KEY-11), pour la session complète. */
@RestController
@RequestMapping("/api/v1/pki")
public class PkiController {

    private final AuthorityQuery authority;

    public PkiController(AuthorityQuery authority) {
        this.authority = authority;
    }

    @GetMapping("/ca")
    public AuthorityView ca() {
        return AuthorityView.of(authority.authority());
    }
}
