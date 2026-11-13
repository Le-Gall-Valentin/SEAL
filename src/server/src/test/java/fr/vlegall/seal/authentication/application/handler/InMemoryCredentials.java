package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.StoredCredential;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.common.UserId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** {@link CredentialRepository} en mémoire, partagé par les tests des cas d'usage d'{@code authentication}. */
final class InMemoryCredentials implements CredentialRepository {

    private final Map<UserId, String> hashes = new HashMap<>();
    private final Map<UserId, Long> versions = new HashMap<>();

    @Override
    public void save(UserId user, String passwordHash) {
        hashes.put(user, passwordHash);
        versions.put(user, 1L);
    }

    @Override
    public Optional<StoredCredential> find(UserId user) {
        return Optional.ofNullable(hashes.get(user)).map(hash -> new StoredCredential(hash, versions.get(user)));
    }

    @Override
    public boolean changeHash(UserId user, String passwordHash) {
        if (hashes.replace(user, passwordHash) == null) {
            return false;
        }
        versions.merge(user, 1L, Long::sum);
        return true;
    }

    @Override
    public boolean lock(UserId user) {
        return hashes.containsKey(user);
    }

    /** Hachés enregistrés, par compte. */
    Map<UserId, String> hashes() {
        return hashes;
    }
}
