package fr.vlegall.seal.conversations.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.ConversationException;
import fr.vlegall.seal.conversations.domain.model.MemberProfile;
import fr.vlegall.seal.conversations.domain.port.out.FriendshipPort;
import fr.vlegall.seal.conversations.domain.port.out.MemberDirectoryPort;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FRD-21, ADR 0039 (point 2) : chaque compte visé par une directe, une création ou un ajout doit être un ami de
 * l'appelant et avoir un compte actif. Le contrôle d'amitié ne lit que les amitiés ; l'annuaire écarte en plus un compte
 * supprimé dont l'amitié subsisterait (défense en profondeur, ACC-07 effaçant les amitiés). Partagé par les handlers ;
 * appelé dans leur transaction.
 */
@ApplicationService
public class ActiveFriends {

    private final FriendshipPort friendships;
    private final MemberDirectoryPort directory;

    public ActiveFriends(FriendshipPort friendships, MemberDirectoryPort directory) {
        this.friendships = friendships;
        this.directory = directory;
    }

    /**
     * Refuse par le même {@link ConversationException.NotFriends} un inconnu, un non-ami, un ancien ami, un compte
     * inactif ou l'appelant lui-même : rien ne trahit qu'un compte existe. Un ensemble vide passe.
     */
    public void require(UserId caller, Set<UserId> others) {
        if (others.isEmpty()) {
            return;
        }
        if (!friendships.areAllFriends(caller, others) || !allActive(others)) {
            throw new ConversationException.NotFriends();
        }
    }

    private boolean allActive(Set<UserId> users) {
        return directory.activeProfiles(users).stream()
                .map(MemberProfile::user)
                .collect(Collectors.toSet())
                .containsAll(users);
    }
}
