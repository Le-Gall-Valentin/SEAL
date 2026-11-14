package fr.vlegall.seal.keys.infrastructure.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.common.web.Base64Url;
import fr.vlegall.seal.keys.application.port.in.CreateChallengeUseCase;
import fr.vlegall.seal.keys.application.port.in.PublishIdentityUseCase;
import fr.vlegall.seal.keys.application.port.in.PublishSubkeyUseCase;
import fr.vlegall.seal.keys.application.port.in.VaultUseCase;
import fr.vlegall.seal.keys.domain.model.IdentityPublication;
import fr.vlegall.seal.keys.domain.model.SubkeyPublication;
import fr.vlegall.seal.keys.infrastructure.web.dto.CertificateView;
import fr.vlegall.seal.keys.infrastructure.web.dto.ChallengeView;
import fr.vlegall.seal.keys.infrastructure.web.dto.PublishIdentityRequest;
import fr.vlegall.seal.keys.infrastructure.web.dto.PublishSubkeyRequest;
import fr.vlegall.seal.keys.infrastructure.web.dto.VaultRequest;
import fr.vlegall.seal.keys.infrastructure.web.dto.VaultView;
import fr.vlegall.seal.security.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final PublishSubkeyUseCase subkeys;
    private final VaultUseCase vaults;

    public KeysController(
            CreateChallengeUseCase challenges,
            PublishIdentityUseCase identities,
            PublishSubkeyUseCase subkeys,
            VaultUseCase vaults) {
        this.challenges = challenges;
        this.identities = identities;
        this.subkeys = subkeys;
        this.vaults = vaults;
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

    /** KEY-05 : 204 ; statuts de l'ADR 0025, points 5 et 7. */
    @PostMapping("/subkeys")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subkey(@CurrentUser UserId user, @Valid @RequestBody PublishSubkeyRequest body) {
        SubkeyPublication result = subkeys.publish(
                user,
                Base64Url.decode(body.certSerial()),
                Base64Url.decode(body.ekId()),
                Base64Url.decode(body.ekPublic()),
                body.createdAt(),
                body.expiresAt(),
                Base64Url.decode(body.signature()));
        switch (result) {
            case SubkeyPublication.Published published -> {}
            case SubkeyPublication.NoCurrentCertificate none -> throw new SealException(ErrorCode.KEY_NOT_FOUND);
            case SubkeyPublication.OutOfBounds bounds ->
                throw new SealException(
                        ErrorCode.VALIDATION_FAILED, bounds.fields().toArray(String[]::new));
            case SubkeyPublication.InvalidSignature invalid -> throw new SealException(ErrorCode.INVALID_SIGNATURE);
            case SubkeyPublication.DuplicateEkId duplicate ->
                throw new SealException(ErrorCode.VALIDATION_FAILED, "ekId");
        }
    }

    /** KEY-33 : le coffre du compte, 404 {@code KEY_NOT_FOUND} s'il n'en a pas encore. */
    @GetMapping("/vault")
    public VaultView vault(@CurrentUser UserId user) {
        return vaults.read(user).map(VaultView::of).orElseThrow(() -> new SealException(ErrorCode.KEY_NOT_FOUND));
    }

    /** KEY-32 : 204, ou 409 {@code VAULT_VERSION_CONFLICT} si la version ne suit pas l'enregistrée (ADR 0025, point 6). */
    @PutMapping("/vault")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveVault(@CurrentUser UserId user, @Valid @RequestBody VaultRequest body) {
        if (!vaults.save(user, body.toVault())) {
            throw new SealException(ErrorCode.VAULT_VERSION_CONFLICT);
        }
    }
}
