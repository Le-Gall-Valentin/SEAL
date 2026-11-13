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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AUTH-70, ADR 0023 : une connexion par l'ancien mot de passe, dont la vérification Argon2id chevauche la
 * réinitialisation, ouvre une session qui ne sert à rien. Le hacheur est retenu après la lecture du haché, le temps que
 * la réinitialisation passe : la course est rejouée à coup sûr. Contexte propre à cette classe (hacheur retenu).
 */
@SealIntegrationTest
class LoginResetRaceIT {

    private static final Pattern TOKEN = Pattern.compile("#jeton=([A-Za-z0-9_-]{43})");

    static volatile boolean armed;
    static final CountDownLatch REACHED = new CountDownLatch(1);
    static final CountDownLatch RELEASE = new CountDownLatch(1);

    /** Retient une seule vérification de mot de passe, une fois armée, jusqu'au signal du test. */
    @TestConfiguration(proxyBeanMethods = false)
    static class HeldVerification {

        @Bean
        static BeanPostProcessor heldPasswordVerification() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof PasswordHasher hasher)) {
                        return bean;
                    }
                    return new PasswordHasher() {
                        @Override
                        public String hash(Password password) {
                            return hasher.hash(password);
                        }

                        @Override
                        public boolean matches(Password password, String encoded) {
                            if (armed) {
                                armed = false;
                                REACHED.countDown();
                                try {
                                    RELEASE.await(20, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
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
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Test
    @DisplayName("AUTH-70 : une connexion par l'ancien mot de passe pendant la réinitialisation n'ouvre aucune session"
            + " utilisable (ADR 0023)")
    void auth70LoginRacingTheResetGetsNoUsableSession() throws Exception {
        String username = "course" + UUID.randomUUID().toString().substring(0, 8);
        AuthClient owner = new AuthClient(mvc, mail);
        Registered account = owner.register(username + "@exemple.ca", username);
        owner.verify(account);
        mail.reset();
        AuthClient attacker = new AuthClient(mvc, mail);
        mvc.perform(new AuthClient(mvc, mail)
                .unsafe(post("/api/v1/auth/password-reset/request"), null, Map.of("email", account.email())));
        Matcher link = TOKEN.matcher(mail.sentTo(account.email()).getLast().getText());
        assertThat(link.find()).as("lien dans le courriel").isTrue();
        var racingLogin =
                attacker.unsafe(post("/api/v1/auth/login"), null, Map.of("login", username, "password", PASSWORD));

        armed = true;
        ExecutorService pool = Executors.newSingleThreadExecutor();
        MvcResult loggedIn;
        try {
            Future<MvcResult> login = pool.submit(() -> mvc.perform(racingLogin).andReturn());
            assertThat(REACHED.await(20, TimeUnit.SECONDS))
                    .as("haché lu, vérification retenue")
                    .isTrue();
            mvc.perform(new AuthClient(mvc, mail)
                            .unsafe(
                                    post("/api/v1/auth/password-reset/confirm"),
                                    null,
                                    Map.of("token", link.group(1), "password", "nouvelle-phrase-de-passe")))
                    .andExpect(status().isNoContent());
            RELEASE.countDown();
            loggedIn = login.get(30, TimeUnit.SECONDS);
        } finally {
            RELEASE.countDown();
            pool.shutdownNow();
        }

        Cookie raced = AuthClient.sessionOf(loggedIn);
        mvc.perform(attacker.from(get("/api/v1/auth/me")).cookie(raced))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
