package fr.vlegall.seal.authentication.infrastructure.identity;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.AccountLookupQuery;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Demande le compte d'une connexion ou d'une réinitialisation au contexte {@code identity}, par son port d'entrée (ADR
 * 0008, règle 7).
 */
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

    @Override
    public Optional<AccountContact> findActiveByEmail(String email) {
        return accounts.findActiveByEmail(email).map(AccountLookupAdapter::toContact);
    }

    @Override
    public Optional<AccountContact> findActiveContact(UserId user) {
        return accounts.findActiveContact(user).map(AccountLookupAdapter::toContact);
    }

    private static AccountContact toContact(AccountLookupQuery.Contact contact) {
        return new AccountContact(contact.user(), contact.username(), contact.email(), contact.personalWords());
    }
}
