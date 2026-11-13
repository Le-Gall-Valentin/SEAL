package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.common.UserId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** {@link CredentialRepository} en mémoire, partagé par les tests des cas d'usage d'{@code authentication}. */
final class InMemoryCredentials implements CredentialRepository {

    private final Map<UserId, String> hashes = new HashMap<>();

    @Override
    public void save(UserId user, String passwordHash) {
        hashes.put(user, passwordHash);
    }

    @Override
    public Optional<String> findHash(UserId user) {
        return Optional.ofNullable(hashes.get(user));
    }

    @Override
    public boolean changeHash(UserId user, String passwordHash) {
        return hashes.replace(user, passwordHash) != null;
    }

    /** Hachés enregistrés, par compte. */
    Map<UserId, String> hashes() {
        return hashes;
    }
}
