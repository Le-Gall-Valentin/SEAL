package fr.vlegall.seal.friends.infrastructure.security;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.port.out.FriendsEventPort;
import fr.vlegall.seal.security.journal.SecurityEvent;
import fr.vlegall.seal.security.journal.SecurityEventType;
import fr.vlegall.seal.security.journal.SecurityJournal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * FRD-02, SEC-20, ADR 0037 (point 5) : gestes des amis dans {@code security_events}, dans la transaction du geste.
 * Seuls des identifiants internes sont notés : ni identifiant saisi, ni nom affiché, ni courriel (SEC-21).
 */
@Component
public class FriendsJournalAdapter implements FriendsEventPort {

    private final SecurityJournal journal;

    public FriendsJournalAdapter(SecurityJournal journal) {
        this.journal = journal;
    }

    @Override
    public void requestSent(UserId sender, UserId receiver, UUID requestId) {
        journal.recordInTransaction(new SecurityEvent(
                SecurityEventType.FRIEND_REQUEST_SENT,
                sender,
                Map.of("otherUserId", receiver.value().toString(), "requestId", requestId.toString())));
    }

    @Override
    public void requestAccepted(UserId receiver, UserId sender, UUID requestId) {
        journal.recordInTransaction(new SecurityEvent(
                SecurityEventType.FRIEND_REQUEST_ACCEPTED,
                receiver,
                Map.of("otherUserId", sender.value().toString(), "requestId", requestId.toString())));
    }

    @Override
    public void friendRemoved(UserId user, UserId formerFriend) {
        journal.recordInTransaction(new SecurityEvent(
                SecurityEventType.FRIEND_REMOVED,
                user,
                Map.of("otherUserId", formerFriend.value().toString())));
    }
}
