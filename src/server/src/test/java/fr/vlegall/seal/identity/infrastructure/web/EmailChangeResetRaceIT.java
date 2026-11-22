package fr.vlegall.seal.identity.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * ACC-02, ADR 0034 : une demande de réinitialisation qui chevauche un changement de courriel ne laisse aucun lien
 * valable à l'ancienne adresse. Le verrou de {@code user_credentials} est tenu par une connexion à part : la demande,
 * qui a déjà lu l'ancienne adresse, l'attend, et le changement se confirme pendant ce temps. La course est rejouée à
 * coup sûr.
 */
@SealIntegrationTest
class EmailChangeResetRaceIT {

    private static final Pattern LINK =
            Pattern.compile("(?m)^https://\\S+/mot-de-passe/nouveau#jeton=([A-Za-z0-9_-]{43})$");
    private static final Pattern CODE = Pattern.compile("code de vérification SEAL : ([0-9]{6})\\.");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void reset() {
        mail.reset();
    }

    /** Requêtes qui attendent un verrou de ligne sur {@code user_credentials}. */
    private int waitingOnCredentials() {
        return jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                        + "AND query ILIKE '%user_credentials%'")
                .query(Integer.class)
                .single();
    }

    private void awaitWaiters(int expected, Future<?> orDone) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (waitingOnCredentials() < expected
                && !orDone.isDone()
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
    }

    @Test
    @DisplayName(
            "ACC-02, ADR 0034 : un lien demandé pendant un changement de courriel ne vaut rien à l'ancienne adresse")
    void acc02ResetRequestRacingTheChangeLeavesNoLiveLink() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 6);
        AuthClient owner = new AuthClient(mvc, mail);
        Registered account = owner.register("ancienne" + run + "@exemple.ca", "course" + run);
        Cookie session = owner.verify(account);
        String target = "neuve" + run + "@exemple.ca";
        mvc.perform(owner.unsafe(post("/api/v1/account/email"), session, Map.of("email", target)));
        Matcher code = CODE.matcher(mail.sentTo(target).getLast().getText());
        assertThat(code.find()).isTrue();
        mail.reset();
        var resetRequest = new AuthClient(mvc, mail)
                .unsafe(post("/api/v1/auth/password-reset/request"), null, Map.of("email", account.email()));
        var confirmation = owner.unsafe(post("/api/v1/account/email/confirm"), session, Map.of("code", code.group(1)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        MvcResult confirmed;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("SELECT 1 FROM user_credentials WHERE user_id = ? FOR UPDATE")) {
                lock.setObject(1, account.id());
                lock.executeQuery().close();
            }
            Future<MvcResult> request =
                    pool.submit(() -> mvc.perform(resetRequest).andReturn());
            awaitWaiters(1, request);
            Future<MvcResult> confirm =
                    pool.submit(() -> mvc.perform(confirmation).andReturn());
            awaitWaiters(2, confirm);
            holder.rollback();
            confirmed = confirm.get(30, TimeUnit.SECONDS);
            request.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        List<SimpleMailMessage> sent = mail.sentTo(account.email());
        for (SimpleMailMessage message : sent) {
            Matcher link = LINK.matcher(message.getText() == null ? "" : message.getText());
            if (link.find()) {
                int inspected = mvc.perform(new AuthClient(mvc, mail)
                                .unsafe(
                                        post("/api/v1/auth/password-reset/inspect"),
                                        null,
                                        Map.of("token", link.group(1))))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                assertThat(inspected).as("lien envoyé à l'ancienne adresse").isEqualTo(422);
            }
        }
    }
}
