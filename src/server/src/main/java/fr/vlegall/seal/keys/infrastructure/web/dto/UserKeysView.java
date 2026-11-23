package fr.vlegall.seal.keys.infrastructure.web.dto;

import fr.vlegall.seal.keys.domain.model.UserKeys;
import java.util.List;

/** KEY-33, API-02 : réponse de {@code GET /api/v1/users/{userId}/keys} (ADR 0029, point 4 ; ADR 0037, point 7). */
public record UserKeysView(CertificateView certificate, List<SubkeyView> subkeys) {

    public UserKeysView {
        subkeys = List.copyOf(subkeys);
    }

    public static UserKeysView of(UserKeys keys) {
        return new UserKeysView(
                CertificateView.of(keys.certificate()),
                keys.subkeys().stream().map(SubkeyView::of).toList());
    }
}
