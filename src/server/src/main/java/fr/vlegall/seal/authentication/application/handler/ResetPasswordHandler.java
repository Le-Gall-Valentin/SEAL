package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.application.port.in.ResetPasswordUseCase;
import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.LiveReset;
import fr.vlegall.seal.authentication.domain.model.ResetOutcome;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.authentication.domain.port.out.SessionRevocationPort;
import fr.vlegall.seal.common.ApplicationService;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ApplicationService
public class ResetPasswordHandler implements ResetPasswordUseCase {

    private final PasswordResetTokenRepository tokens;
    private final AccountLookupPort accounts;
    private final PasswordPreparation preparation;
    private final PasswordResetWriter writer;
    private final SessionRevocationPort sessions;
    private final SecurityEventPort journal;
    private final Clock clock;

    public ResetPasswordHandler(
            PasswordResetTokenRepository tokens,
            AccountLookupPort accounts,
            PasswordPreparation preparation,
            PasswordResetWriter writer,
            SessionRevocationPort sessions,
            SecurityEventPort journal,
            Clock clock) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.preparation = preparation;
        this.writer = writer;
        this.sessions = sessions;
        this.journal = journal;
        this.clock = clock;
    }

    /**
     * {@code NEVER} : Argon2id ne retient aucune connexion (ADR 0020), et la révocation, qui refuse une transaction
     * active, suit la validation de l'écriture. Un lien invalide ne coûte aucun calcul. Les sessions tombent avant le
     * journal, comme à la déconnexion : un journal indisponible ne les laisse pas ouvertes (ADR 0022, point 6).
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ResetOutcome reset(String token, String password) {
        Optional<LiveReset> live =
                ResetToken.parse(token).flatMap(parsed -> tokens.findLive(parsed.hash(), clock.instant()));
        Optional<AccountContact> contact = live.flatMap(reset -> accounts.findActiveContact(reset.user()));
        if (live.isEmpty() || contact.isEmpty()) {
            return new ResetOutcome.InvalidToken();
        }
        Optional<String> hash = preparation.prepare(password, contact.get().personalWords());
        if (hash.isEmpty()) {
            return new ResetOutcome.WeakPassword();
        }
        if (!writer.apply(live.get(), hash.get(), clock.instant())) {
            return new ResetOutcome.InvalidToken();
        }
        sessions.revokeAll(live.get().user());
        journal.passwordReset(live.get().user());
        return new ResetOutcome.Done();
    }
}
