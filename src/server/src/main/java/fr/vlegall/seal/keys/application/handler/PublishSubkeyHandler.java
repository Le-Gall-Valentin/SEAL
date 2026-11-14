package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.PublishSubkeyUseCase;
import fr.vlegall.seal.keys.domain.model.CurrentCertificate;
import fr.vlegall.seal.keys.domain.model.SubkeyBinding;
import fr.vlegall.seal.keys.domain.model.SubkeyPublication;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class PublishSubkeyHandler implements PublishSubkeyUseCase {

    /** ADR 0025, point 7 : écart toléré entre l'horloge du client et celle du serveur. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

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
        List<String> outOfBounds = new ArrayList<>();
        Instant now = clock.instant();
        if (createdAt.isBefore(now.minus(CLOCK_SKEW)) || createdAt.isAfter(now.plus(CLOCK_SKEW))) {
            outOfBounds.add("createdAt");
        }
        if (!expiresAt.equals(createdAt.plus(SubkeyBinding.VALIDITY))) {
            outOfBounds.add("expiresAt");
        }
        if (!outOfBounds.isEmpty()) {
            return new SubkeyPublication.OutOfBounds(outOfBounds);
        }
        SubkeyBinding binding =
                new SubkeyBinding(user, certSerial, ekId, SubkeyBinding.KEM, ekPublic, createdAt, expiresAt);
        if (!binding.isSignedBy(current.get().ikPublic(), signature)) {
            return new SubkeyPublication.InvalidSignature();
        }
        if (!subkeys.save(binding, signature)) {
            return new SubkeyPublication.DuplicateEkId();
        }
        events.subkeyPublished(user, ekId);
        return new SubkeyPublication.Published();
    }
}
