package fr.vlegall.seal.authentication.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.web.ClientRoutesController;
import fr.vlegall.seal.security.web.OAuthRoutes;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Départ OAuth que le filtre de Spring n'a pas résolu (ADR 0034, point 2). Pour une session complète, c'est un
 * fournisseur que son compte n'a pas lié, ou qui n'est pas configuré : la ré-authentification est refusée sans
 * aller-retour, et la navigation revient aux paramètres avec {@code REQUEST_REJECTED}. Pour l'anonyme, un fournisseur
 * inconnu garde son 404 (OPS-03) ; la session restreinte est refusée avant (403, {@code SecurityConfig}).
 */
@Controller
public class OAuthStartController {

    @GetMapping(OAuthRoutes.AUTHORIZATION_BASE + "/{registrationId}")
    public void unresolvedStart(@Nullable Authentication authentication, HttpServletResponse response)
            throws IOException {
        boolean fullSession = authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> SealAuthorities.ACCOUNT_ACTIVE.equals(authority.getAuthority()));
        if (!fullSession) {
            throw new SealException(ErrorCode.NOT_FOUND);
        }
        response.sendRedirect(ClientRoutesController.SETTINGS_REQUEST_REJECTED);
    }
}
