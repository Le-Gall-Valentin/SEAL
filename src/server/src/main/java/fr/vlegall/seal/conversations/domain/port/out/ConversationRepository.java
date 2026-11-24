package fr.vlegall.seal.conversations.domain.port.out;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.Conversation;
import fr.vlegall.seal.conversations.domain.model.ConversationId;
import fr.vlegall.seal.conversations.domain.model.DirectKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Conversations et membres ({@code conversations}, {@code conversation_members}, ADR 0039). */
public interface ConversationRepository {

    /** Identifiant UUIDv7 d'une conversation à créer. */
    ConversationId nextId();

    /** CNV-01 : crée la directe de la paire et ses deux membres si elle n'existe pas ; vrai si cet appel l'a créée. */
    boolean createDirect(DirectKey key, UserId a, UserId b, Instant at);

    Optional<Conversation> findDirect(DirectKey key);

    /** CNV-03 : le groupe et ses membres initiaux. */
    void createGroup(Conversation group);

    /** CNV-06 : la conversation dont {@code member} est membre actif, sans verrou ; vide sinon. */
    Optional<Conversation> findForMember(ConversationId id, UserId member);

    /**
     * GRP-03 : comme {@link #findForMember}, verrouillée jusqu'à la fin de la transaction ({@code FOR UPDATE}) ;
     * l'appartenance est jugée une fois le verrou obtenu : un membre exclu pendant l'attente reçoit vide.
     */
    Optional<Conversation> lockForMember(ConversationId id, UserId member);

    /** Enregistre le passage de {@code before} à {@code after} : arrivées, départs (à {@code at}), rôles et nom. */
    void save(Conversation before, Conversation after, Instant at);

    /** CNV-04 : conversations où {@code member} est actif, après {@code after}, triées par identifiant. */
    List<Conversation> pageForMember(UserId member, Optional<ConversationId> after, int limit);

    /** API-02 : vrai si les deux comptes sont membres actifs d'un même groupe ; une directe ne compte pas. */
    boolean shareActiveGroup(UserId a, UserId b);
}
