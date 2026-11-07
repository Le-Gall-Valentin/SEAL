package fr.vlegall.seal.security.journal;

import fr.vlegall.seal.common.UserId;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Événement de sécurité à journaliser (SEC-20).
 *
 * @param type nature de l'événement
 * @param userId utilisateur concerné, s'il est connu
 * @param details précisions ; l'appelant masque lui-même les courriels (SEC-21) et n'y met jamais de secret
 */
public record SecurityEvent(
        SecurityEventType type, @Nullable UserId userId, Map<String, String> details) {

    public SecurityEvent {
        Objects.requireNonNull(type, "type");
        details = Map.copyOf(details);
    }
}
