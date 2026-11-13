package fr.vlegall.seal.authentication.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.LiveReset;
import fr.vlegall.seal.authentication.domain.port.out.CredentialRepository;
import fr.vlegall.seal.authentication.domain.port.out.PasswordResetTokenRepository;
import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.crypto.Digests;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@SealIntegrationTest
class PasswordResetStorageIT {

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofMinutes(30));

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private CredentialRepository credentials;

    @Autowired
    private JdbcClient jdbc;

    private UserId insertUser() {
        String username = "reset." + UUID.randomUUID().toString().substring(0, 8);
        return new UserId(jdbc.sql("INSERT INTO users (username, display_name) VALUES (?, 'Nom') RETURNING id")
                .param(username)
                .query(UUID.class)
                .single());
    }

    private static byte[] hash(String seed) {
        return Digests.sha256((seed + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("AUTH-70 : un lien émis se retrouve par son haché, avec son compte et son expiration")
    void auth70IssuedTokenIsFoundByItsHash() {
        UserId user = insertUser();
        byte[] hash = hash("a");

        tokens.issue(user, hash, EXPIRES, NOW);

        assertThat(tokens.findLive(hash, NOW)).hasValueSatisfying(live -> {
            assertThat(live.user()).isEqualTo(user);
            assertThat(live.expiresAt()).isEqualTo(EXPIRES);
        });
        assertThat(jdbc.sql("SELECT token_hash FROM password_reset_tokens WHERE user_id = ?")
                        .param(user.value())
                        .query(byte[].class)
                        .single())
                .as("seul le haché est écrit")
                .isEqualTo(hash);
    }

    @Test
    @DisplayName("AUTH-70 : un nouveau lien invalide les précédents du compte, pas ceux d'un autre (ADR 0022)")
    void auth70NewTokenInvalidatesTheOlderOnes() {
        UserId user = insertUser();
        UserId other = insertUser();
        byte[] first = hash("premier");
        byte[] second = hash("second");
        byte[] others = hash("autre");
        tokens.issue(other, others, EXPIRES, NOW);
        tokens.issue(user, first, EXPIRES, NOW);

        tokens.issue(user, second, EXPIRES, NOW.plusSeconds(60));

        assertThat(tokens.findLive(first, NOW.plusSeconds(61))).isEmpty();
        assertThat(tokens.findLive(second, NOW.plusSeconds(61))).isPresent();
        assertThat(tokens.findLive(others, NOW.plusSeconds(61))).isPresent();
    }

    @Test
    @DisplayName("AUTH-70 : un lien ne se consomme qu'une fois, puis n'est plus trouvé")
    void auth70TokenIsConsumedOnce() {
        UserId user = insertUser();
        byte[] hash = hash("unique");
        tokens.issue(user, hash, EXPIRES, NOW);
        LiveReset live = tokens.findLive(hash, NOW).orElseThrow();

        assertThat(tokens.consume(live.id(), NOW.plusSeconds(1))).isTrue();
        assertThat(tokens.consume(live.id(), NOW.plusSeconds(2))).isFalse();
        assertThat(tokens.findLive(hash, NOW.plusSeconds(3))).isEmpty();
    }

    @Test
    @DisplayName("AUTH-70 : un lien expiré après 30 minutes n'est ni trouvé ni consommé")
    void auth70ExpiredTokenIsNeitherFoundNorConsumed() {
        UserId user = insertUser();
        byte[] hash = hash("expire");
        tokens.issue(user, hash, EXPIRES, NOW);
        UUID id = tokens.findLive(hash, NOW).orElseThrow().id();

        assertThat(tokens.findLive(hash, EXPIRES)).isEmpty();
        assertThat(tokens.consume(id, EXPIRES)).isFalse();
        assertThat(tokens.findLive(hash, EXPIRES.minus(1, ChronoUnit.MILLIS))).isPresent();
    }

    @Test
    @DisplayName("AUTH-70 : un succès invalide tous les liens du compte")
    void auth70ConsumeAllInvalidatesEveryToken() {
        UserId user = insertUser();
        byte[] hash = hash("tous");
        tokens.issue(user, hash, EXPIRES, NOW);

        tokens.consumeAll(user, NOW.plusSeconds(5));

        assertThat(tokens.findLive(hash, NOW.plusSeconds(6))).isEmpty();
    }

    @Test
    @DisplayName("AUTH-70 : deux consommations simultanées du même lien : une seule réussit")
    void auth70ConcurrentConsumptionSucceedsOnce() throws Exception {
        UserId user = insertUser();
        byte[] hash = hash("course");
        tokens.issue(user, hash, EXPIRES, NOW);
        UUID id = tokens.findLive(hash, NOW).orElseThrow().id();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(
                    pool.submit(() -> {
                        start.await();
                        return tokens.consume(id, NOW.plusSeconds(1));
                    }),
                    pool.submit(() -> {
                        start.await();
                        return tokens.consume(id, NOW.plusSeconds(1));
                    }));
            start.countDown();

            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("AUTH-70 : le nouveau haché remplace l'ancien ; un compte sans mot de passe n'en reçoit pas")
    void auth70ChangeHashUpdatesOnlyAnExistingCredential() {
        UserId user = insertUser();
        UserId oauthOnly = insertUser();
        credentials.save(user, "{argon2}ancien");

        assertThat(credentials.changeHash(user, "{argon2}nouveau")).isTrue();
        assertThat(credentials.findHash(user)).contains("{argon2}nouveau");
        assertThat(credentials.changeHash(oauthOnly, "{argon2}nouveau")).isFalse();
        assertThat(credentials.findHash(oauthOnly)).isEmpty();
    }
}
