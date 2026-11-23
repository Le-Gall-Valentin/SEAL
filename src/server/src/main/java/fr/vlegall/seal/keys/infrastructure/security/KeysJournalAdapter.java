package fr.vlegall.seal.keys.infrastructure.security;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.model.SignedStructure;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SEC-20 : publications de clés dans {@code security_events}, dans la transaction de la publication ; A09 : signatures
 * refusées en WARN (ADR 0025, point 10). Seuls des identifiants publics sont notés : numéro de série du certificat,
 * {@code ekId} ; jamais de clé.
 */
@Component
public class KeysJournalAdapter implements KeyEventPort {

    private static final Logger LOG = LoggerFactory.getLogger(KeysJournalAdapter.class);

    private final SecurityJournal journal;

    public KeysJournalAdapter(SecurityJournal journal) {
        this.journal = journal;
    }

    @Override
    public void identityPublished(UserId user, byte[] serial) {
        journal.recordInTransaction(new SecurityEvent(
                SecurityEventType.IDENTITY_KEY_PUBLISHED,
                user,
                Map.of("serial", HexFormat.of().formatHex(serial))));
    }

    @Override
    public void identityReset(UserId user, byte[] serial, Optional<byte[]> revokedSerial) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("serial", HexFormat.of().formatHex(serial));
        revokedSerial.ifPresent(
                revoked -> details.put("revokedSerial", HexFormat.of().formatHex(revoked)));
        journal.recordInTransaction(new SecurityEvent(SecurityEventType.IDENTITY_KEY_RESET, user, details));
    }

    /**
     * A09 (CDC section 15) : alerte sur les échecs cryptographiques répétés, avec la route et le compte, pour voir qui
     * les accumule ; aucune valeur reçue, ni clé, ni signature, ni défi (SEC-21). L'identifiant de corrélation du
     * journal relie la ligne à sa requête.
     */
    @Override
    public void signatureRejected(UserId user, SignedStructure structure) {
        LOG.warn("Signature refusée (INVALID_SIGNATURE) sur {}, compte {}", route(structure), user.value());
    }

    private static String route(SignedStructure structure) {
        return switch (structure) {
            case PROOF_OF_POSSESSION -> "PUT /api/v1/keys/identity";
            case RESET_PROOF_OF_POSSESSION -> "POST /api/v1/keys/identity/reset";
            case SUBKEY_BINDING -> "POST /api/v1/keys/subkeys";
        };
    }

    @Override
    public void subkeyPublished(UserId user, byte[] ekId) {
        journal.recordInTransaction(new SecurityEvent(
                SecurityEventType.SUBKEY_PUBLISHED,
                user,
                Map.of("ekId", HexFormat.of().formatHex(ekId))));
    }
}
