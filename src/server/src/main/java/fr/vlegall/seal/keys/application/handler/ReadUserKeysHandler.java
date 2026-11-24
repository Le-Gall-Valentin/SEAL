package fr.vlegall.seal.keys.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.application.port.in.UserKeysQuery;
import fr.vlegall.seal.keys.domain.model.UserKeys;
import fr.vlegall.seal.keys.domain.port.out.CertificationPort;
import fr.vlegall.seal.keys.domain.port.out.RelationPort;
import fr.vlegall.seal.keys.domain.port.out.SubkeyRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ReadUserKeysHandler implements UserKeysQuery {

    private final CertificationPort certification;
    private final SubkeyRepository subkeys;
    private final List<RelationPort> relations;
    private final Clock clock;

    public ReadUserKeysHandler(
            CertificationPort certification, SubkeyRepository subkeys, List<RelationPort> relations, Clock clock) {
        this.certification = certification;
        this.subkeys = subkeys;
        this.relations = List.copyOf(relations);
        this.clock = clock;
    }

    /**
     * SEC-03, API-02 : l'appelant lui-même, un ami du titulaire ou un membre actif d'un groupe commun (ADR 0037, point
     * 7 ; ADR 0039, point 7), contrôlé avant toute lecture ; médiation complète, sans cache (ADR 0025). Tout autre
     * appelant, ancien ami avec une directe compris, reçoit la même absence qu'un compte sans certificat (A01).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserKeys> read(UserId caller, UserId owner) {
        if (!caller.equals(owner) && relations.stream().noneMatch(relation -> relation.related(caller, owner))) {
            return Optional.empty();
        }
        return certification
                .current(owner)
                .map(certificate ->
                        new UserKeys(certificate, subkeys.findLive(owner, certificate.serial(), clock.instant())));
    }
}
