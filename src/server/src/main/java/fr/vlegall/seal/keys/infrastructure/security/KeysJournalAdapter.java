package fr.vlegall.seal.keys.infrastructure.security;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.keys.domain.port.out.KeyEventPort;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.HexFormat;
import java.util.Map;
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

    /**
     * A09 (CDC section 15) : alerte sur les échecs cryptographiques ; une ligne fixe, sans compte ni octet reçu (SEC-21),
     * reliée à sa requête par l'identifiant de corrélation du journal.
     */
    @Override
    public void signatureRejected() {
        LOG.warn("Signature refusée à la publication d'une clé (INVALID_SIGNATURE)");
    }

    @Override
    public void subkeyPublished(UserId user, byte[] ekId) {
        journal.recordInTransaction(new SecurityEvent(
                SecurityEventType.SUBKEY_PUBLISHED,
                user,
                Map.of("ekId", HexFormat.of().formatHex(ekId))));
    }
}
