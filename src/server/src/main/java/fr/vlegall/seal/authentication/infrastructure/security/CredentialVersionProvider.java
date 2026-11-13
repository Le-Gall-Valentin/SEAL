package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.CredentialVersionQuery;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.CredentialVersions;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** Fournit à {@code security} la version du mot de passe, par le cas d'usage {@link CredentialVersionQuery} (ADR 0023). */
@Component
public class CredentialVersionProvider implements CredentialVersions {

    private final CredentialVersionQuery versions;

    public CredentialVersionProvider(CredentialVersionQuery versions) {
        this.versions = versions;
    }

    @Override
    public OptionalLong current(UserId user) {
        return versions.currentVersion(user);
    }
}
