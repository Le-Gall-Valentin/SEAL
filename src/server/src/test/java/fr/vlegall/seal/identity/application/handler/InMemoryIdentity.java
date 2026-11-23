package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.InvitationPolicy;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import fr.vlegall.seal.identity.domain.model.LiveEmailChange;
import fr.vlegall.seal.identity.domain.model.PreparedPassword;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.AccountMailPort;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.InitialPasswordPort;
import fr.vlegall.seal.identity.domain.port.out.PasswordResetLinksPort;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import fr.vlegall.seal.identity.domain.port.out.ResendAllowance;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.identity.domain.port.out.SudoModePort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
import fr.vlegall.seal.identity.domain.port.out.UserSettingsRepository;
import fr.vlegall.seal.support.MutableClock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Doublures en mémoire des ports sortants d'{@code identity}, écrites à la main, avec une horloge fixe. */
final class InMemoryIdentity {

    static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");
    static final String VERIFY = "VERIFY_EMAIL";
    static final String CHANGE = "CHANGE_EMAIL";

    /** Code enregistré, tel que la table {@code email_codes} le garderait, avec son usage et sa cible. */
    record StoredCode(
            UUID id,
            UserId user,
            String purpose,
            @Nullable EmailAddress target,
            byte[] hash,
            Instant issuedAt,
            Instant expiresAt,
            int attempts,
            boolean consumed) {

        StoredCode consume() {
            return new StoredCode(id, user, purpose, target, hash, issuedAt, expiresAt, attempts, true);
        }

        StoredCode failedAttempt() {
            return new StoredCode(id, user, purpose, target, hash, issuedAt, expiresAt, attempts + 1, consumed);
        }

        boolean isLive(String wanted, Instant now) {
            return purpose.equals(wanted) && !consumed && expiresAt.isAfter(now) && attempts < CodePolicy.MAX_ATTEMPTS;
        }
    }

    final MutableClock clock = MutableClock.fixedAt(NOW);
    final Map<UserId, Account> accounts = new LinkedHashMap<>();
    final List<StoredCode> codes = new ArrayList<>();
    final List<String> mails = new ArrayList<>();
    final List<String> events = new ArrayList<>();
    final Map<UserId, Long> resendsRemaining = new HashMap<>();
    final Deque<String> digits = new ArrayDeque<>(List.of("123456"));
    boolean passwordAccepted = true;
    final List<List<String>> personalWords = new ArrayList<>();
    final Map<UserId, String> storedPasswords = new HashMap<>();
    /** Mode sudo de la session (AUTH-55) : faux, toute demande de changement de courriel est refusée. */
    boolean sudoActive = true;
    /** Comptes dont les liens de réinitialisation ont été invalidés (ADR 0034). */
    final List<UserId> resetLinksInvalidated = new ArrayList<>();
    /** ACC-06 : politique d'invitation enregistrée ; sans entrée, pas de ligne dans {@code user_settings}. */
    final Map<UserId, InvitationPolicy> policies = new HashMap<>();

    Account add(String username, String email, AccountStatus status) {
        UserId id = new UserId(UUID.randomUUID());
        Account account = new Account(
                id,
                Username.parse(username),
                DisplayName.parse("Nom").orElseThrow(),
                EmailAddress.parse(email).orElseThrow(),
                status);
        accounts.put(id, account);
        return account;
    }

    UserRepository users() {
        return new UserRepository() {
            @Override
            public UserId createPending(Username username, DisplayName displayName, EmailAddress email) {
                if (exists(username)) {
                    throw new IdentityException.UsernameTaken();
                }
                UserId id = new UserId(UUID.randomUUID());
                accounts.put(id, new Account(id, username, displayName, email, AccountStatus.PENDING_EMAIL));
                return id;
            }

            @Override
            public UserId createActive(
                    Username username, DisplayName displayName, EmailAddress email, Instant verifiedAt) {
                if (exists(username)) {
                    throw new IdentityException.UsernameTaken();
                }
                if (activeAccountOwns(email)) {
                    throw new IdentityException.EmailInUse();
                }
                UserId id = new UserId(UUID.randomUUID());
                accounts.put(id, new Account(id, username, displayName, email, AccountStatus.ACTIVE));
                return id;
            }

            @Override
            public Optional<Account> findById(UserId id) {
                return Optional.ofNullable(accounts.get(id));
            }

            @Override
            public Optional<Account> lockById(UserId id) {
                return findById(id);
            }

            @Override
            public void activate(UserId id, Instant verifiedAt) {
                Account account = accounts.get(id);
                if (activeAccountOwns(account.email())) {
                    throw new IdentityException.CodeExpired();
                }
                accounts.put(
                        id,
                        new Account(
                                id, account.username(), account.displayName(), account.email(), AccountStatus.ACTIVE));
            }

            @Override
            public void changeEmail(UserId id, EmailAddress email) {
                Account account = accounts.get(id);
                accounts.put(id, new Account(id, account.username(), account.displayName(), email, account.status()));
            }

            @Override
            public void changeActiveEmail(UserId id, EmailAddress email, Instant verifiedAt) {
                if (activeAccountOwns(email)) {
                    throw new IdentityException.CodeExpired();
                }
                Account account = accounts.get(id);
                accounts.put(id, new Account(id, account.username(), account.displayName(), email, account.status()));
            }

            @Override
            public void changeDisplayName(UserId id, DisplayName displayName) {
                Account account = accounts.get(id);
                accounts.put(id, new Account(id, account.username(), displayName, account.email(), account.status()));
            }

            @Override
            public boolean activeAccountOwns(EmailAddress email) {
                return accounts.values().stream()
                        .anyMatch(account -> account.status() == AccountStatus.ACTIVE
                                && account.email() != null
                                && account.email().value().equalsIgnoreCase(email.value()));
            }

            @Override
            public List<Account> findAll(Collection<UserId> ids) {
                // Ordre inverse de la demande : le vrai dépôt ne garantit aucun ordre.
                return ids.stream()
                        .distinct()
                        .map(accounts::get)
                        .filter(Objects::nonNull)
                        .toList()
                        .reversed();
            }

            @Override
            public Optional<Account> findActiveByUsername(Username username) {
                return accounts.values().stream()
                        .filter(account ->
                                account.isActive() && account.username().equals(username))
                        .findFirst();
            }

            @Override
            public Optional<UserId> findNotDeletedByUsername(Username username) {
                return accounts.values().stream()
                        .filter(account -> account.status() != AccountStatus.DELETED
                                && account.username().equals(username))
                        .map(Account::id)
                        .findFirst();
            }

            @Override
            public Optional<UserId> findActiveByEmail(EmailAddress email) {
                return accounts.values().stream()
                        .filter(account -> account.status() == AccountStatus.ACTIVE
                                && account.email() != null
                                && account.email().value().equalsIgnoreCase(email.value()))
                        .map(Account::id)
                        .findFirst();
            }

            @Override
            public boolean exists(Username username) {
                return accounts.values().stream()
                        .anyMatch(account -> account.username().equals(username));
            }

            @Override
            public Set<String> takenAmong(Collection<Username> candidates) {
                return candidates.stream()
                        .filter(this::exists)
                        .map(Username::value)
                        .collect(Collectors.toSet());
            }
        };
    }

    UserSettingsRepository settings() {
        return user -> policies.getOrDefault(user, InvitationPolicy.EVERYONE);
    }

    EmailCodeRepository emailCodes() {
        return new EmailCodeRepository() {
            @Override
            public void renew(UserId user, byte[] codeHash, Instant issuedAt, Instant expiresAt) {
                retire(user, VERIFY);
                codes.add(
                        new StoredCode(UUID.randomUUID(), user, VERIFY, null, codeHash, issuedAt, expiresAt, 0, false));
            }

            @Override
            public Optional<Instant> liveExpiry(UserId user, Instant now) {
                return live(user, VERIFY, now).map(StoredCode::expiresAt);
            }

            @Override
            public Optional<LiveCode> findLive(UserId user, Instant now) {
                return live(user, VERIFY, now).map(code -> new LiveCode(code.id(), code.hash()));
            }

            @Override
            public void renewChange(
                    UserId user, EmailAddress target, byte[] codeHash, Instant issuedAt, Instant expiresAt) {
                retire(user, CHANGE);
                codes.add(new StoredCode(
                        UUID.randomUUID(), user, CHANGE, target, codeHash, issuedAt, expiresAt, 0, false));
            }

            @Override
            public Optional<LiveEmailChange> findLiveChange(UserId user, Instant now) {
                return live(user, CHANGE, now)
                        .map(code ->
                                new LiveEmailChange(code.id(), code.hash(), Objects.requireNonNull(code.target())));
            }

            private void retire(UserId user, String purpose) {
                codes.replaceAll(
                        code -> code.user().equals(user) && code.purpose().equals(purpose) && !code.consumed()
                                ? code.consume()
                                : code);
            }

            private Optional<StoredCode> live(UserId user, String purpose, Instant now) {
                return codes.stream()
                        .filter(code -> code.user().equals(user) && code.isLive(purpose, now))
                        .reduce((first, second) -> second);
            }

            @Override
            public void countFailedAttempt(UUID id) {
                codes.replaceAll(code -> code.id().equals(id) ? code.failedAttempt() : code);
            }

            @Override
            public void consume(UUID id, Instant now) {
                codes.replaceAll(code -> code.id().equals(id) ? code.consume() : code);
            }
        };
    }

    InitialPasswordPort credentials() {
        return new InitialPasswordPort() {
            @Override
            public Optional<PreparedPassword> prepare(String password, List<String> words) {
                personalWords.add(words);
                return passwordAccepted ? Optional.of(new PreparedPassword("hash:" + password)) : Optional.empty();
            }

            @Override
            public void store(UserId user, PreparedPassword password) {
                storedPasswords.put(user, password.hash());
            }
        };
    }

    RegisterHandler registerHandler() {
        return new RegisterHandler(
                users(), credentials(), new PendingAccountWriter(users(), credentials(), journal(), codeDelivery()));
    }

    AccountMailPort mail() {
        return new AccountMailPort() {
            @Override
            public void sendCode(EmailAddress to, Username username, VerificationCode code) {
                mails.add("code:" + to.value() + ":" + username.value() + ":" + code.value());
            }

            @Override
            public void sendExistingAccountWarning(EmailAddress to) {
                mails.add("warning:" + to.value());
            }

            @Override
            public void sendEmailChangeCode(EmailAddress to, Username username, VerificationCode code) {
                mails.add("change-code:" + to.value() + ":" + username.value() + ":" + code.value());
            }

            @Override
            public void sendEmailTakenWarning(EmailAddress to) {
                mails.add("taken-warning:" + to.value());
            }

            @Override
            public void sendEmailChangedNotice(EmailAddress oldEmail, Username username, EmailAddress newEmail) {
                mails.add("changed-notice:" + oldEmail.value() + ":" + username.value() + ":" + newEmail.value());
            }
        };
    }

    SudoModePort sudo() {
        return () -> {
            if (!sudoActive) {
                throw new SealException(ErrorCode.REAUTH_REQUIRED);
            }
        };
    }

    PasswordResetLinksPort resetLinks() {
        return resetLinksInvalidated::add;
    }

    SecurityEventPort journal() {
        return new SecurityEventPort() {
            @Override
            public void accountRegistered(UserId user, EmailAddress email) {
                events.add("registered:" + user.value() + ":" + email.value());
            }

            @Override
            public void emailChanged(UserId user, EmailAddress newEmail) {
                events.add("email-changed:" + user.value() + ":" + newEmail.value());
            }
        };
    }

    ResendAllowance allowance() {
        return user -> resendsRemaining.getOrDefault(user, 3L);
    }

    RandomDigits random() {
        return length -> {
            String next = digits.size() > 1 ? digits.pop() : digits.getFirst();
            return next.substring(0, length);
        };
    }

    CodeDelivery codeDelivery() {
        return new CodeDelivery(users(), emailCodes(), mail(), random(), clock);
    }

    /** Code émis pour un compte, comme le ferait l'inscription. */
    String issueCode(Account account, String digitsOfCode) {
        this.digits.clear();
        this.digits.add(digitsOfCode);
        codeDelivery().issue(account.id(), account.username(), account.email());
        return digitsOfCode;
    }

    StoredCode lastCodeOf(UserId user) {
        return codes.stream()
                .filter(code -> code.user().equals(user))
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
