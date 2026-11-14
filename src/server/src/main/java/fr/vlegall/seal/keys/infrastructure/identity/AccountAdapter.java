package fr.vlegall.seal.keys.infrastructure.identity;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.AccountLookupQuery;
import fr.vlegall.seal.keys.domain.port.out.AccountPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Demande l'identifiant d'un compte actif au contexte {@code identity}, par son port d'entrée (ADR 0008, règle 7). */
@Component("keysAccountAdapter")
public class AccountAdapter implements AccountPort {

    private final AccountLookupQuery accounts;

    public AccountAdapter(AccountLookupQuery accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<String> activeUsername(UserId user) {
        return accounts.findActiveContact(user).map(AccountLookupQuery.Contact::username);
    }
}
