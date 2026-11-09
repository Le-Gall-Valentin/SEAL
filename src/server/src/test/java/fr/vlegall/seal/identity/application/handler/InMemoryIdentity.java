package fr.vlegall.seal.identity.application.handler;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.identity.domain.model.Account;
import fr.vlegall.seal.identity.domain.model.AccountStatus;
import fr.vlegall.seal.identity.domain.model.CodePolicy;
import fr.vlegall.seal.identity.domain.model.DisplayName;
import fr.vlegall.seal.identity.domain.model.EmailAddress;
import fr.vlegall.seal.identity.domain.model.IdentityException;
import fr.vlegall.seal.identity.domain.model.LiveCode;
import fr.vlegall.seal.identity.domain.model.Username;
import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.identity.domain.port.out.AccountMailPort;
import fr.vlegall.seal.identity.domain.port.out.EmailCodeRepository;
import fr.vlegall.seal.identity.domain.port.out.InitialPasswordPort;
import fr.vlegall.seal.identity.domain.port.out.RandomDigits;
import fr.vlegall.seal.identity.domain.port.out.ResendAllowance;
import fr.vlegall.seal.identity.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.identity.domain.port.out.UserRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Doublures en mémoire des ports sortants d'{@code identity}, écrites à la main, avec une horloge fixe. */
final class InMemoryIdentity {

    static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    /** Code enregistré, tel que la table {@code email_codes} le garderait. */
    record StoredCode(
            UUID id, UserId user, byte[] hash, Instant issuedAt, Instant expiresAt, int attempts, boolean consumed) {

        StoredCode consume() {
            return new StoredCode(id, user, hash, issuedAt, expiresAt, attempts, true);
        }

        StoredCode failedAttempt() {
            return new StoredCode(id, user, hash, issuedAt, expiresAt, attempts + 1, consumed);
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
            public boolean activeAccountOwns(EmailAddress email) {
                return accounts.values().stream()
                        .anyMatch(account -> account.status() == AccountStatus.ACTIVE
                                && account.email() != null
                                && account.email().value().equalsIgnoreCase(email.value()));
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

    EmailCodeRepository emailCodes() {
        return new EmailCodeRepository() {
            @Override
            public void renew(UserId user, byte[] codeHash, Instant issuedAt, Instant expiresAt) {
                codes.replaceAll(code -> code.user().equals(user) && !code.consumed() ? code.consume() : code);
                codes.add(new StoredCode(UUID.randomUUID(), user, codeHash, issuedAt, expiresAt, 0, false));
            }

            @Override
            public Optional<Instant> liveExpiry(UserId user, Instant now) {
                return live(user, now).map(StoredCode::expiresAt);
            }

            @Override
            public Optional<LiveCode> findLive(UserId user, Instant now) {
                return live(user, now).map(code -> new LiveCode(code.id(), code.hash()));
            }

            private Optional<StoredCode> live(UserId user, Instant now) {
                return codes.stream()
                        .filter(code -> code.user().equals(user)
                                && !code.consumed()
                                && code.expiresAt().isAfter(now)
                                && code.attempts() < CodePolicy.MAX_ATTEMPTS)
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
        return (user, password, words) -> {
            personalWords.add(words);
            return passwordAccepted;
        };
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
        };
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
