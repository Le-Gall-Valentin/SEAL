package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ApplicationService;
import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.application.port.in.CreateOAuthAccountUseCase;
import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.ReservedUsernames;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/** AUTH-32 : compte d'une inscription OAuth, dans la transaction de l'appelant, qui lie ensuite l'identité. */
@ApplicationService
public class CreateOAuthAccountHandler implements CreateOAuthAccountUseCase {

    private final UserRepository users;
    private final SecurityEventPort journal;
    private final CodeDelivery codes;
    private final Clock clock;

    public CreateOAuthAccountHandler(UserRepository users, SecurityEventPort journal, CodeDelivery codes, Clock clock) {
        this.users = users;
        this.journal = journal;
        this.codes = codes;
        this.clock = clock;
    }

    /**
     * Les index uniques restent juges : un identifiant pris entre-temps donne {@code USERNAME_TAKEN}, un courriel vérifié
     * activé entre-temps {@code OAUTH_EMAIL_IN_USE}, et la transaction est annulée.
     */
    @Override
    @Transactional
    public UserId create(String username, String displayName, String email, boolean emailVerified) {
        Username chosen = Username.parse(username);
        if (ReservedUsernames.contains(chosen.value())) {
            throw new IdentityException.UsernameReserved();
        }
        if (users.exists(chosen)) {
            throw new IdentityException.UsernameTaken();
        }
        DisplayName name = DisplayName.parse(displayName)
                .orElseThrow(() -> new SealException(ErrorCode.VALIDATION_FAILED, "displayName"));
        EmailAddress address = EmailAddress.parse(email)
                .orElseThrow(() -> new IllegalArgumentException("courriel du fournisseur invalide"));
        if (emailVerified) {
            if (users.activeAccountOwns(address)) {
                throw new IdentityException.EmailInUse();
            }
            UserId user = users.createActive(chosen, name, address, clock.instant());
            journal.accountRegistered(user, address);
            return user;
        }
        UserId user = users.createPending(chosen, name, address);
        journal.accountRegistered(user, address);
        codes.issue(user, chosen, address);
        return user;
    }
}
