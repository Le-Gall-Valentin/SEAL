package fr.vlegall.seal.authentication.infrastructure.web;

import static fr.vlegall.seal.support.AuthClient.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.authentication.domain.model.Password;
import fr.vlegall.seal.authentication.domain.port.out.PasswordHasher;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ACC-01, ADR 0023 et 0034 : courses autour du changement de mot de passe, rejouées à coup sûr en retenant une
 * opération du hacheur le temps que l'autre geste passe. Contexte propre à cette classe (hacheur retenu).
 */
@SealIntegrationTest
@Import(PasswordChangeRaceIT.HeldHasher.class)
class PasswordChangeRaceIT {

    private static final String NEW_PASSWORD = "raquette-sous-la-neige";
    private static final Pattern TOKEN = Pattern.compile("#jeton=([A-Za-z0-9_-]{43})");

    /** Opération du hacheur à retenir une fois : {@code hash} ou {@code matches}. */
    static volatile String armed = "";

    static volatile CountDownLatch reached = new CountDownLatch(1);
    static volatile CountDownLatch release = new CountDownLatch(1);

    @TestConfiguration(proxyBeanMethods = false)
    static class HeldHasher {

        @Bean
        static BeanPostProcessor heldPasswordHasher() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof PasswordHasher hasher)) {
                        return bean;
                    }
                    return new PasswordHasher() {
                        @Override
                        public String hash(Password password) {
                            hold("hash");
                            return hasher.hash(password);
                        }

                        @Override
                        public boolean matches(Password password, String encoded) {
                            hold("matches");
                            return hasher.matches(password, encoded);
                        }

                        @Override
                        public boolean recognises(String encoded) {
                            return hasher.recognises(encoded);
                        }

                        @Override
                        public void verifyDecoy(Password password) {
                            hasher.verifyDecoy(password);
                        }
                    };
                }
            };
        }

        private static void hold(String operation) {
            synchronized (PasswordChangeRaceIT.class) {
                if (!operation.equals(armed)) {
                    return;
                }
                armed = "";
            }
            reached.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @AfterEach
    void reset() {
        armed = "";
        release.countDown();
        mail.reset();
    }

    private static void arm(String operation) {
        reached = new CountDownLatch(1);
        release = new CountDownLatch(1);
        armed = operation;
    }

    private int loginStatus(String login, String password) throws Exception {
        return mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(post("/api/v1/auth/login"), null, Map.of("login", login, "password", password)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    @DisplayName(
            "ACC-01, ADR 0034 : une réinitialisation qui aboutit pendant le changement l'emporte ; le changement répond 401")
    void acc01ResetDuringTheChangeWins() throws Exception {
        String name = "course" + UUID.randomUUID().toString().substring(0, 6);
        AuthClient owner = new AuthClient(mvc, mail);
        Registered account = owner.register(name + "@exemple.ca", name);
        Cookie full = owner.verify(account);
        mail.reset();
        mvc.perform(new AuthClient(mvc, mail)
                .unsafe(post("/api/v1/auth/password-reset/request"), null, Map.of("email", account.email())));
        Matcher link = TOKEN.matcher(mail.sentTo(account.email()).getLast().getText());
        assertThat(link.find()).isTrue();
        var change = owner.unsafe(
                post("/api/v1/account/password"),
                full,
                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD));

        arm("hash");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        MvcResult changed;
        try {
            Future<MvcResult> pending = pool.submit(() -> mvc.perform(change).andReturn());
            assertThat(reached.await(20, TimeUnit.SECONDS))
                    .as("mot de passe actuel vérifié, hachage retenu")
                    .isTrue();
            mvc.perform(new AuthClient(mvc, mail)
                            .unsafe(
                                    post("/api/v1/auth/password-reset/confirm"),
                                    null,
                                    Map.of("token", link.group(1), "password", "phrase-de-la-reinitialisation")))
                    .andExpect(status().isNoContent());
            release.countDown();
            changed = pending.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertThat(changed.getResponse().getStatus()).isEqualTo(401);
        assertThat(loginStatus(name, "phrase-de-la-reinitialisation")).isEqualTo(204);
        assertThat(loginStatus(name, NEW_PASSWORD)).isEqualTo(401);
    }

    @Test
    @DisplayName(
            "ACC-01, ADR 0023 : une connexion par l'ancien mot de passe pendant le changement n'ouvre aucune session utilisable")
    void acc01LoginRacingTheChangeGetsNoUsableSession() throws Exception {
        String name = "coursl" + UUID.randomUUID().toString().substring(0, 6);
        AuthClient owner = new AuthClient(mvc, mail);
        Registered account = owner.register(name + "@exemple.ca", name);
        Cookie full = owner.verify(account);
        AuthClient attacker = new AuthClient(mvc, mail);
        var racingLogin =
                attacker.unsafe(post("/api/v1/auth/login"), null, Map.of("login", name, "password", PASSWORD));

        arm("matches");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        MvcResult loggedIn;
        try {
            Future<MvcResult> login = pool.submit(() -> mvc.perform(racingLogin).andReturn());
            assertThat(reached.await(20, TimeUnit.SECONDS))
                    .as("haché lu, vérification retenue")
                    .isTrue();
            mvc.perform(owner.unsafe(
                            post("/api/v1/account/password"),
                            full,
                            Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)))
                    .andExpect(status().isNoContent());
            release.countDown();
            loggedIn = login.get(30, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        Cookie raced = AuthClient.sessionOf(loggedIn);
        mvc.perform(attacker.from(get("/api/v1/auth/me")).cookie(raced))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
