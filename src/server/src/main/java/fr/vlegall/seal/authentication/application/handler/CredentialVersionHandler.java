package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.CredentialVersionQuery;
import fr.vlegall.seal.authentication.domain.model.StoredCredential;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.UserId;
import java.util.OptionalLong;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class CredentialVersionHandler implements CredentialVersionQuery {

    private final CredentialRepository credentials;

    public CredentialVersionHandler(CredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalLong currentVersion(UserId user) {
        return credentials
                .find(user)
                .map(StoredCredential::version)
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }
}
