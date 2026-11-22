package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.authentication.application.port.in.LoginMethodsQuery;
import fr.vlegall.seal.authentication.infrastructure.web.dto.LoginMethodsResponse;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comptes liés, en lecture seule (14.2 du CDC, avancée par l'ADR 0035, point 2). La règle d'accès est dans
 * {@code SecurityConfig} : session complète seulement. Le contexte {@code authentication} possède le mot de passe et
 * les liaisons OAuth (ADR 0008).
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountIdentitiesController {

    private final LoginMethodsQuery methods;

    public AccountIdentitiesController(LoginMethodsQuery methods) {
        this.methods = methods;
    }

    /** AUTH-55, ADR 0035 : ce que la ré-authentification peut proposer à ce compte. */
    @GetMapping("/identities")
    public LoginMethodsResponse identities(@CurrentUser UserId user) {
        return LoginMethodsResponse.of(methods.of(user));
    }
}
