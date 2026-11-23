package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.keys.application.port.in.ResetIdentityUseCase;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityReset;
import fr.vlegall.seal.keys.domain.model.ProofOfPossession;
import fr.vlegall.seal.keys.domain.model.SignedStructure;
import fr.vlegall.seal.keys.domain.model.Vault;
import fr.vlegall.seal.keys.domain.port.out.AccountPort;
import fr.vlegall.seal.keys.domain.port.out.CertificateReplacementPort;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.ChallengeRepository;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import fr.vlegall.seal.keys.domain.port.out.SudoModePort;
import fr.vlegall.seal.keys.domain.port.out.VaultRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ResetIdentityHandler implements ResetIdentityUseCase {

    private final SudoModePort sudo;
    private final AccountPort accounts;
    private final ChallengeRepository challenges;
    private final VaultRepository vaults;
    private final CertificationPort certification;
    private final CertificateReplacementPort replacement;
    private final KeyEventPort events;
    private final Clock clock;

    public ResetIdentityHandler(
            SudoModePort sudo,
            AccountPort accounts,
            ChallengeRepository challenges,
            VaultRepository vaults,
            CertificationPort certification,
            CertificateReplacementPort replacement,
            KeyEventPort events,
            Clock clock) {
        this.sudo = sudo;
        this.accounts = accounts;
        this.challenges = challenges;
        this.vaults = vaults;
        this.certification = certification;
        this.replacement = replacement;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Une seule transaction (ADR 0036, point 1) : défi, coffre, révocation et émission (dans celle de {@code pki}),
     * journal. Le mode sudo et le compte sont vérifiés avant de toucher au défi ; la même IK est refusée avant
     * d'écrire le coffre ; un remplacement devancé lève une exception, pour que le coffre déjà écrit soit annulé.
     */
    @Override
    @Transactional
    public IdentityReset reset(UserId user, byte[] ikPublic, byte[] challenge, byte[] signature, Vault vault) {
        sudo.require();
        String username = accounts.activeUsername(user).orElseThrow(() -> new SealException(ErrorCode.FORBIDDEN));
        // La réinitialisation remplace un coffre ; elle n'en crée pas (ADR 0036, point 1).
        if (vaults.find(user).isEmpty()) {
            return new IdentityReset.NoVault();
        }
        if (!challenges.consume(user, Digests.sha256(challenge), clock.instant())) {
            return new IdentityReset.ChallengeExpired();
        }
        // userId vient de la session : une preuve signée pour un autre compte ne vérifie pas (PROTOCOL.md, 4.3).
        if (!new ProofOfPossession(user, ikPublic, challenge).isSignedBy(signature)) {
            events.signatureRejected(user, SignedStructure.RESET_PROOF_OF_POSSESSION);
            return new IdentityReset.InvalidSignature();
        }
        // ADR 0036, point 1.5 : rien à réinitialiser ; refusé avant le coffre, le défi reste consommé (KEY-03).
        Optional<IdentityCertificate> current = certification.current(user);
        if (current.filter(held -> Arrays.equals(held.ikPublic(), ikPublic)).isPresent()) {
            return new IdentityReset.SameKey();
        }
        // ADR 0036, point 2 : la version suit l'enregistrée ; de deux réinitialisations, la seconde ne change rien.
        if (!vaults.save(user, vault)) {
            return new IdentityReset.VaultConflict();
        }
        // Une publication concurrente qui devance le nouveau certificat annule tout, coffre compris.
        IdentityCertificate certificate = replacement
                .supersede(user, username, ikPublic)
                .orElseThrow(() -> new SealException(ErrorCode.FORBIDDEN));
        events.identityReset(user, certificate.serial(), current.map(IdentityCertificate::serial));
        return new IdentityReset.Reset(certificate);
    }
}
