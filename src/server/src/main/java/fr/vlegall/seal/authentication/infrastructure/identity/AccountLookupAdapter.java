package fr.vlegall.seal.authentication.infrastructure.identity;

import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.AccountLookupQuery;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Demande le compte d'une connexion au contexte {@code identity}, par son port d'entrée (ADR 0008, règle 7). */
@Component
public class AccountLookupAdapter implements AccountLookupPort {

    private final AccountLookupQuery accounts;

    public AccountLookupAdapter(AccountLookupQuery accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<UserId> findForLogin(String login) {
        return accounts.findForLogin(login);
    }

    @Override
    public boolean isVerified(UserId user) {
        return accounts.isVerified(user);
    }
}
