package fr.vlegall.seal.keys.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.keys.application.port.in.CreateChallengeUseCase;
import fr.vlegall.seal.keys.application.port.in.PublishIdentityUseCase;
import fr.vlegall.seal.keys.domain.model.IdentityPublication;
import fr.vlegall.seal.keys.infrastructure.web.dto.CertificateView;
import fr.vlegall.seal.keys.infrastructure.web.dto.ChallengeView;
import fr.vlegall.seal.keys.infrastructure.web.dto.PublishIdentityRequest;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clés du compte de la session (KEY-03 à KEY-05, KEY-32), réservées à la session complète dans {@code SecurityConfig}.
 * Aucun identifiant de compte dans l'URL ni dans le corps : chaque route ne touche que les clés de l'appelant (A01).
 */
@RestController
@RequestMapping("/api/v1/keys")
public class KeysController {

    private final CreateChallengeUseCase challenges;
    private final PublishIdentityUseCase identities;

    public KeysController(CreateChallengeUseCase challenges, PublishIdentityUseCase identities) {
        this.challenges = challenges;
        this.identities = identities;
    }

    /** KEY-03 : 32 octets, 5 minutes ; remplace le défi précédent (ADR 0025, point 8). */
    @PostMapping("/challenge")
    public ChallengeView challenge(@CurrentUser UserId user) {
        return ChallengeView.of(challenges.create(user));
    }

    /** KEY-03, KEY-04 : statuts de l'ADR 0025, point 5. */
    @PutMapping("/identity")
    public CertificateView identity(@CurrentUser UserId user, @Valid @RequestBody PublishIdentityRequest body) {
        IdentityPublication result = identities.publish(
                user,
                Base64Url.decode(body.ikPublic()),
                Base64Url.decode(body.challenge()),
                Base64Url.decode(body.signature()));
        return switch (result) {
            case IdentityPublication.Published published -> CertificateView.of(published.certificate());
            case IdentityPublication.ChallengeExpired expired -> throw new SealException(ErrorCode.CODE_EXPIRED);
            case IdentityPublication.InvalidSignature invalid -> throw new SealException(ErrorCode.INVALID_SIGNATURE);
            case IdentityPublication.Refused refused -> throw new SealException(ErrorCode.FORBIDDEN);
        };
    }
}
