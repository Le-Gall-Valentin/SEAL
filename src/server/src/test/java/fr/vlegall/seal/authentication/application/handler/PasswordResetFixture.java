package fr.vlegall.seal.authentication.application.handler;

import fr.vlegall.seal.authentication.domain.model.AccountContact;
import fr.vlegall.seal.authentication.domain.model.LiveReset;
import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.model.ResetToken;
import fr.vlegall.seal.authentication.domain.port.out.AccountLookupPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetMailPort;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.authentication.domain.port.out.RandomBytes;
import fr.vlegall.seal.authentication.domain.port.out.SecurityEventPort;
import fr.vlegall.seal.authentication.domain.port.out.SessionRevocationPort;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.MutableClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Doublures en mémoire du parcours « mot de passe oublié » (AUTH-70) : chaque effet s'inscrit dans {@link #calls}, dans
 * l'ordre, pour que les tests vérifient aussi la séquence.
 */
final class PasswordResetFixture {

    static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    static final String PASSWORD = "fjord-tuque-raquette";

    final MutableClock clock = MutableClock.fixedAt(NOW);
    final List<String> calls = new ArrayList<>();
    final InMemoryCredentials credentials = new InMemoryCredentials();
    final Map<UserId, AccountContact> contacts = new HashMap<>();
    final List<Row> rows = new ArrayList<>();
    final List<SentLink> mails = new ArrayList<>();
    private int nextRandom = 1;

    /** Exécuté pendant le hachage : simule ce qui arrive pendant le calcul Argon2id. */
    Runnable duringHash = () -> {};

    record Row(
            UUID id,
            UserId user,
            byte[] hash,
            Instant expiresAt,
            @Nullable Instant consumedAt) {

        Row consumed(Instant at) {
            return new Row(id, user, hash, expiresAt, at);
        }

        boolean isLive(Instant now) {
            return consumedAt == null && expiresAt.isAfter(now);
        }
    }

    record SentLink(String email, String username, ResetToken token) {}

    /** Compte actif, avec un mot de passe si {@code withPassword}. */
    AccountContact active(String username, boolean withPassword) {
        UserId user = new UserId(UUID.randomUUID());
        AccountContact contact =
                new AccountContact(user, username, username + "@exemple.ca", List.of(username, username));
        contacts.put(user, contact);
        if (withPassword) {
            credentials.save(user, "hash(ancien-mot-de-passe-long)");
        }
        return contact;
    }

    /** Lien émis comme le ferait la demande, avec son jeton en clair pour le test. */
    ResetToken issue(UserId user) {
        ResetToken token = ResetToken.of(random().next(ResetToken.BYTES));
        tokens.issue(user, token.hash(), clock.instant().plus(ResetToken.VALIDITY), clock.instant());
        calls.clear();
        return token;
    }

    final AccountLookupPort accounts = new AccountLookupPort() {
        @Override
        public Optional<UserId> findForLogin(String login) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isVerified(UserId user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AccountContact> findActiveByEmail(String email) {
            return contacts.values().stream()
                    .filter(contact -> contact.email().equals(email))
                    .findFirst();
        }

        @Override
        public Optional<AccountContact> findActiveContact(UserId user) {
            return Optional.ofNullable(contacts.get(user));
        }
    };

    final PasswordResetTokenRepository tokens = new PasswordResetTokenRepository() {
        @Override
        public void issue(UserId user, byte[] tokenHash, Instant expiresAt, Instant now) {
            consumeAll(user, now);
            rows.add(new Row(UUID.randomUUID(), user, tokenHash, expiresAt, null));
            calls.add("émission");
        }

        @Override
        public Optional<LiveReset> findLive(byte[] tokenHash, Instant now) {
            return rows.stream()
                    .filter(row -> Arrays.equals(row.hash(), tokenHash) && row.isLive(now))
                    .map(row -> new LiveReset(row.id(), row.user(), row.expiresAt()))
                    .findFirst();
        }

        @Override
        public boolean consume(UUID id, Instant now) {
            calls.add("consommation");
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.id().equals(id) && row.isLive(now)) {
                    rows.set(i, row.consumed(now));
                    return true;
                }
            }
            return false;
        }

        @Override
        public void consumeAll(UserId user, Instant now) {
            rows.replaceAll(row -> row.user().equals(user) && row.isLive(now) ? row.consumed(now) : row);
        }
    };

    final PasswordHasher hasher = new PasswordHasher() {
        @Override
        public String hash(Password password) {
            calls.add("hachage");
            duringHash.run();
            return "hash(" + password.value() + ")";
        }

        @Override
        public boolean matches(Password password, String encoded) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean recognises(String encoded) {
            return encoded.startsWith("hash(");
        }

        @Override
        public void verifyDecoy(Password password) {
            throw new UnsupportedOperationException();
        }
    };

    final PasswordResetMailPort mail = (email, username, token) -> {
        mails.add(new SentLink(email, username, token));
        calls.add("courriel");
    };

    final SessionRevocationPort sessions = user -> calls.add("révocation:" + user.value());

    final SecurityEventPort journal = new SecurityEventPort() {
        @Override
        public void loginSucceeded(UserId user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void loginFailed(@Nullable UserId user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void loggedOut(UserId user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void passwordReset(UserId user) {
            calls.add("journal:" + user.value());
        }
    };

    /** Octets prévisibles et tous différents : 1, puis 2, puis 3… */
    RandomBytes random() {
        return count -> {
            byte[] bytes = new byte[count];
            Arrays.fill(bytes, (byte) nextRandom++);
            return bytes;
        };
    }

    PasswordPreparation preparation() {
        return new PasswordPreparation(Set.of("correcthorsebatterystaple")::contains, hasher);
    }
}
