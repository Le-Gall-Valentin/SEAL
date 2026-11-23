package fr.vlegall.seal.friends.infrastructure.identity;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.friends.domain.model.Person;
import fr.vlegall.seal.friends.domain.port.out.DirectoryPort;
import fr.vlegall.seal.identity.application.port.in.UserDirectoryQuery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Demande les comptes actifs au contexte {@code identity}, par son port d'entrée (ADR 0008, règle 7). */
@Component("friendsDirectoryAdapter")
public class DirectoryAdapter implements DirectoryPort {

    private final UserDirectoryQuery directory;

    public DirectoryAdapter(UserDirectoryQuery directory) {
        this.directory = directory;
    }

    @Override
    public Optional<Person> findActiveByUsername(String username) {
        return directory.findActiveByUsername(username).map(DirectoryAdapter::person);
    }

    @Override
    public List<Person> activeProfiles(Collection<UserId> users) {
        return directory.activeProfiles(users).stream()
                .map(DirectoryAdapter::person)
                .toList();
    }

    @Override
    public boolean acceptsInvitations(UserId user) {
        return directory.acceptsInvitations(user);
    }

    private static Person person(UserDirectoryQuery.Profile profile) {
        return new Person(profile.user(), profile.username(), profile.displayName());
    }
}
