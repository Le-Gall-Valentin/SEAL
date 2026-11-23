package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.UserKeysQuery;
import fr.vlegall.seal.keys.domain.model.UserKeys;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ReadUserKeysHandler implements UserKeysQuery {

    private final CertificationPort certification;
    private final SubkeyRepository subkeys;
    private final Clock clock;

    public ReadUserKeysHandler(CertificationPort certification, SubkeyRepository subkeys, Clock clock) {
        this.certification = certification;
        this.subkeys = subkeys;
        this.clock = clock;
    }

    /** SEC-03, A01 : contrôle d'appartenance avant toute lecture ; médiation complète, sans cache (ADR 0025). */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserKeys> read(UserId caller, UserId owner) {
        if (!caller.equals(owner)) {
            return Optional.empty();
        }
        return certification
                .current(owner)
                .map(certificate ->
                        new UserKeys(certificate, subkeys.findLive(owner, certificate.serial(), clock.instant())));
    }
}
