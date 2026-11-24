package fr.vlegall.seal.conversations.infrastructure.identity;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.conversations.domain.model.MemberProfile;
import fr.vlegall.seal.conversations.domain.port.out.MemberDirectoryPort;
import fr.vlegall.seal.identity.application.port.in.UserDirectoryQuery;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/** Demande les profils actifs au contexte {@code identity}, par son port d'entrée (ADR 0008, règle 7). */
@Component
public class MemberDirectoryAdapter implements MemberDirectoryPort {

    private final UserDirectoryQuery directory;

    public MemberDirectoryAdapter(UserDirectoryQuery directory) {
        this.directory = directory;
    }

    @Override
    public List<MemberProfile> activeProfiles(Collection<UserId> users) {
        return directory.activeProfiles(users).stream()
                .map(profile -> new MemberProfile(profile.user(), profile.username(), profile.displayName()))
                .toList();
    }
}
