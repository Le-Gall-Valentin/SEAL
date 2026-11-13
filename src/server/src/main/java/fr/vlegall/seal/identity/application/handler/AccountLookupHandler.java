package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.Unicode;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.AccountLookupQuery;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.PersonalWords;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class AccountLookupHandler implements AccountLookupQuery {

    private final UserRepository users;

    public AccountLookupHandler(UserRepository users) {
        this.users = users;
    }

    /**
     * Une saisie qui a la forme d'un identifiant, une fois repliée, le cherche ; sinon, une saisie qui a la forme d'un
     * courriel cherche le compte actif. Le courriel n'est normalisé qu'en NFC : la base le compare sans tenir compte de
     * la casse ({@code citext}), et un passage en minuscules peut l'allonger au-delà de 254 caractères (« İ » donne
     * deux caractères), au point de ne plus retrouver le compte (ADR 0021, mise à jour du 2026-11-13).
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserId> findForLogin(String login) {
        Optional<Username> username = Username.tryParse(Unicode.foldLogin(login));
        if (username.isPresent()) {
            return users.findNotDeletedByUsername(username.get());
        }
        return EmailAddress.parse(login).flatMap(users::findActiveByEmail);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVerified(UserId user) {
        return findActive(user).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Contact> findActiveByEmail(String email) {
        return EmailAddress.parse(email).flatMap(users::findActiveByEmail).flatMap(this::findActiveContact);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Contact> findActiveContact(UserId user) {
        return findActive(user).map(AccountLookupHandler::contactOf);
    }

    private Optional<Account> findActive(UserId user) {
        return users.findById(user).filter(account -> account.status() == AccountStatus.ACTIVE);
    }

    /** Un compte actif a toujours un courriel : seul un compte supprimé n'en a pas ({@link Account}). */
    private static Contact contactOf(Account account) {
        EmailAddress email = Objects.requireNonNull(account.email(), "email");
        return new ActiveContact(
                account.id(), account.username().value(), email.value(), PersonalWords.of(account.username(), email));
    }

    private record ActiveContact(UserId user, String username, String email, List<String> personalWords)
            implements Contact {

        ActiveContact {
            personalWords = List.copyOf(personalWords);
        }
    }
}
