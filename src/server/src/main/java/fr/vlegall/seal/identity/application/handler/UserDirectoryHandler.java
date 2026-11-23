package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.UserDirectoryQuery;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.InvitationPolicy;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import fr.vlegall.seal.identity.domain.port.out.UserSettingsRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class UserDirectoryHandler implements UserDirectoryQuery {

    private final UserRepository users;
    private final UserSettingsRepository settings;

    public UserDirectoryHandler(UserRepository users, UserSettingsRepository settings) {
        this.users = users;
        this.settings = settings;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Profile> findActiveByUsername(String username) {
        return Username.tryParse(username).flatMap(users::findActiveByUsername).map(UserDirectoryHandler::profileOf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Profile> activeProfiles(Collection<UserId> requested) {
        List<UserId> distinct = requested.stream().distinct().toList();
        Map<UserId, Account> found =
                users.findAll(distinct).stream().collect(Collectors.toMap(Account::id, Function.identity()));
        return distinct.stream()
                .map(found::get)
                .filter(Objects::nonNull)
                .filter(Account::isActive)
                .map(UserDirectoryHandler::profileOf)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean acceptsInvitations(UserId user) {
        return settings.invitationPolicy(user) == InvitationPolicy.EVERYONE;
    }

    private static Profile profileOf(Account account) {
        return new ActiveProfile(
                account.id(), account.username().value(), account.displayName().value());
    }

    private record ActiveProfile(UserId user, String username, String displayName) implements Profile {}
}
