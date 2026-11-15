package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.PublishSubkeyUseCase;
import fr.vlegall.seal.keys.domain.model.CurrentCertificate;
import fr.vlegall.seal.keys.domain.model.SignedStructure;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.model.SubkeyPolicy;
import fr.vlegall.seal.keys.domain.model.SubkeyPublication;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class PublishSubkeyHandler implements PublishSubkeyUseCase {

    private final CertificationPort certification;
    private final SubkeyRepository subkeys;
    private final KeyEventPort events;
    private final Clock clock;

    public PublishSubkeyHandler(
            CertificationPort certification, SubkeyRepository subkeys, KeyEventPort events, Clock clock) {
        this.certification = certification;
        this.subkeys = subkeys;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Contrôles dans l'ordre de l'ADR 0025 : certificat courant, bornes des dates, signature, puis, sous le verrou du
     * compte, le plafond de trois sous-clés et l'unicité de {@code ekId}. Aucune exception pour un refus.
     */
    @Override
    @Transactional
    public SubkeyPublication publish(
            UserId user,
            byte[] certSerial,
            byte[] ekId,
            byte[] ekPublic,
            Instant createdAt,
            Instant expiresAt,
            byte[] signature) {
        // Médiation complète : le certificat courant est relu à chaque publication (ADR 0025).
        Optional<CurrentCertificate> current = certification.current(user);
        if (current.isEmpty() || !current.get().hasSerial(certSerial)) {
            return new SubkeyPublication.NoCurrentCertificate();
        }
        // ADR 0025, point 7 : la signature porte sur des millisecondes ; contrôle et enregistrement aussi.
        Instant created = createdAt.truncatedTo(ChronoUnit.MILLIS);
        Instant expires = expiresAt.truncatedTo(ChronoUnit.MILLIS);
        Instant now = clock.instant();
        List<String> outOfBounds = SubkeyPolicy.outOfBounds(created, expires, now);
        if (!outOfBounds.isEmpty()) {
            return new SubkeyPublication.OutOfBounds(outOfBounds);
        }
        SubkeyBinding binding =
                new SubkeyBinding(user, certSerial, ekId, SubkeyBinding.KEM, ekPublic, created, expires);
        if (!binding.isSignedBy(current.get().ikPublic(), signature)) {
            events.signatureRejected(user, SignedStructure.SUBKEY_BINDING);
            return new SubkeyPublication.InvalidSignature();
        }
        subkeys.lockAccount(user);
        if (subkeys.countLive(user, now) >= SubkeyPolicy.MAX_LIVE) {
            return new SubkeyPublication.LimitReached();
        }
        if (!subkeys.save(binding, signature)) {
            return new SubkeyPublication.DuplicateEkId();
        }
        events.subkeyPublished(user, ekId);
        return new SubkeyPublication.Published();
    }
}
