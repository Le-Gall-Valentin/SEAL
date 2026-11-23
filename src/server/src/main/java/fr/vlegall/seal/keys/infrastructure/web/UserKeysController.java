package fr.vlegall.seal.keys.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.UserKeysQuery;
import fr.vlegall.seal.keys.infrastructure.web.dto.UserKeysView;
import fr.vlegall.seal.security.web.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * KEY-33, API-02 : certificat et sous-clés valides d'un compte, réservés à la session complète dans
 * {@code SecurityConfig}, lus par l'appelant lui-même ou par un ami ; tout autre compte reçoit 404 {@code KEY_NOT_FOUND},
 * comme un compte sans certificat (A01, ADR 0029, point 4 ; ADR 0037, point 7).
 */
@RestController
public class UserKeysController {

    private final UserKeysQuery keys;

    public UserKeysController(UserKeysQuery keys) {
        this.keys = keys;
    }

    @GetMapping("/api/v1/users/{userId}/keys")
    public UserKeysView keys(@CurrentUser UserId caller, @PathVariable UUID userId) {
        return keys.read(caller, new UserId(userId))
                .map(UserKeysView::of)
                .orElseThrow(() -> new SealException(ErrorCode.KEY_NOT_FOUND));
    }
}
