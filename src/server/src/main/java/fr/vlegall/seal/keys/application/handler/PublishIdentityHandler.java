package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.keys.application.port.in.PublishIdentityUseCase;
import fr.vlegall.seal.keys.domain.model.IdentityCertificate;
import fr.vlegall.seal.keys.domain.model.IdentityPublication;
import fr.vlegall.seal.keys.domain.model.ProofOfPossession;
import fr.vlegall.seal.keys.domain.port.out.AccountPort;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.ChallengeRepository;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class PublishIdentityHandler implements PublishIdentityUseCase {

    private final ChallengeRepository challenges;
    private final AccountPort accounts;
    private final CertificationPort certification;
    private final KeyEventPort events;
    private final Clock clock;

    public PublishIdentityHandler(
            ChallengeRepository challenges,
            AccountPort accounts,
            CertificationPort certification,
            KeyEventPort events,
            Clock clock) {
        this.challenges = challenges;
        this.accounts = accounts;
        this.certification = certification;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Une seule transaction : consommation du défi, émission du certificat (dans celle de {@code pki}) et journal.
     * Aucune exception pour un refus : le défi consommé le reste (KEY-03).
     */
    @Override
    @Transactional
    public IdentityPublication publish(UserId user, byte[] ikPublic, byte[] challenge, byte[] signature) {
        if (!challenges.consume(user, Digests.sha256(challenge), clock.instant())) {
            return new IdentityPublication.ChallengeExpired();
        }
        // userId vient de la session : une preuve signée pour un autre compte ne vérifie pas (PROTOCOL.md, 4.3).
        if (!new ProofOfPossession(user, ikPublic, challenge).isSignedBy(signature)) {
            return new IdentityPublication.InvalidSignature();
        }
        Optional<IdentityCertificate> certificate =
                accounts.activeUsername(user).flatMap(username -> certification.certify(user, username, ikPublic));
        if (certificate.isEmpty()) {
            return new IdentityPublication.Refused();
        }
        events.identityPublished(user, certificate.get().serial());
        return new IdentityPublication.Published(certificate.get());
    }
}
