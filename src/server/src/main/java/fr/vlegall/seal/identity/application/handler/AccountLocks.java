package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;

/** Verrou du compte de la session, partagé par les gestes de la vérification (A06 : gestes concurrents sérialisés). */
final class AccountLocks {

    private AccountLocks() {}

    /** Compte verrouillé ; une session sans compte existant n'obtient rien (échec fermé, A01). */
    static Account lock(UserRepository users, UserId user) {
        return users.lockById(user)
                .filter(account -> account.status() != AccountStatus.DELETED)
                .orElseThrow(() -> new SealException(ErrorCode.UNAUTHENTICATED));
    }

    /** SEC-03 : renvoi et changement ne valent que pour un compte en attente, en plus de la règle d'URL. */
    static Account lockPending(UserRepository users, UserId user) {
        Account account = lock(users, user);
        if (account.status() != AccountStatus.PENDING_EMAIL) {
            throw new SealException(ErrorCode.FORBIDDEN);
        }
        return account;
    }
}
