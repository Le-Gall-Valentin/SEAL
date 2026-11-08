package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.domain.model.PasswordPolicy;
import fr.vlegall.seal.authentication.domain.port.out.Blocklist;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Liste de blocage d'AUTH-11, embarquée depuis {@code src/protocol} comme ressource Maven (PROTOCOL.md, section 3.1).
 * Échec fermé : sans elle, SEAL ne démarre pas.
 */
@Component
public final class ClasspathBlocklist implements Blocklist {

    static final String RESOURCE = "/password-blocklist.txt";

    private final Set<String> entries;

    public ClasspathBlocklist() {
        this.entries = load();
    }

    @Override
    public boolean contains(String folded) {
        return entries.contains(folded);
    }

    private static Set<String> load() {
        try (InputStream in = ClasspathBlocklist.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("liste de blocage absente : " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(PasswordPolicy::fold)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("liste de blocage illisible : " + RESOURCE, e);
        }
    }
}
