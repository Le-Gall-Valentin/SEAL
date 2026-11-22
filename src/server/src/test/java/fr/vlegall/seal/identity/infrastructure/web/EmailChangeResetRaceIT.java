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
import java.util.ArrayList;
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
 * valable à l'ancienne adresse. Le verrou de {@code user_credentials} est tenu par une connexion à part ; la demande,
 * qui a déjà lu l'ancienne adresse, et la confirmation du changement l'attendent toutes deux, dans l'ordre choisi par
 * le test. Les deux ordres sont rejoués à coup sûr : chacun éprouve l'une des deux protections.
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

    /** Issue d'une course : les statuts des deux requêtes et les liens reçus à l'ancienne adresse. */
    private record Outcome(int requestStatus, int confirmStatus, List<String> links) {}

    /**
     * Rejoue la course : le verrou est tenu, puis la requête {@code first} se met à l'attendre, puis l'autre ; le verrou
     * est enfin relâché, et PostgreSQL le donne dans l'ordre d'attente.
     */
    private Outcome race(boolean confirmationFirst) throws Exception {
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
        MvcResult requested;
        MvcResult confirmed;
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("SELECT 1 FROM user_credentials WHERE user_id = ? FOR UPDATE")) {
                lock.setObject(1, account.id());
                lock.executeQuery().close();
            }
            Future<MvcResult> first = pool.submit(() ->
                    mvc.perform(confirmationFirst ? confirmation : resetRequest).andReturn());
            awaitWaiters(1, first);
            Future<MvcResult> second = pool.submit(() ->
                    mvc.perform(confirmationFirst ? resetRequest : confirmation).andReturn());
            awaitWaiters(2, second);
            holder.rollback();
            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, TimeUnit.SECONDS);
            confirmed = confirmationFirst ? firstResult : secondResult;
            requested = confirmationFirst ? secondResult : firstResult;
        } finally {
            pool.shutdownNow();
        }

        List<String> links = new ArrayList<>();
        for (SimpleMailMessage message : mail.sentTo(account.email())) {
            Matcher link = LINK.matcher(message.getText() == null ? "" : message.getText());
            if (link.find()) {
                links.add(link.group(1));
            }
        }
        return new Outcome(
                requested.getResponse().getStatus(), confirmed.getResponse().getStatus(), List.copyOf(links));
    }

    private int inspect(String token) throws Exception {
        return mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(post("/api/v1/auth/password-reset/inspect"), null, Map.of("token", token)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    @DisplayName(
            "ACC-02, ADR 0034 : la demande passe d'abord ; son lien, envoyé à l'ancienne adresse, meurt au changement")
    void acc02RequestFirstGetsALinkThatDies() throws Exception {
        Outcome outcome = race(false);

        assertThat(outcome.requestStatus()).isEqualTo(204);
        assertThat(outcome.confirmStatus()).isEqualTo(200);
        assertThat(outcome.links()).hasSize(1);
        assertThat(inspect(outcome.links().getFirst()))
                .as("lien de l'ancienne adresse")
                .isEqualTo(422);
    }

    @Test
    @DisplayName("ACC-02, ADR 0034 : le changement passe d'abord ; la demande relit l'adresse et n'envoie aucun lien")
    void acc02ConfirmationFirstLeavesNoLink() throws Exception {
        Outcome outcome = race(true);

        assertThat(outcome.confirmStatus()).isEqualTo(200);
        assertThat(outcome.requestStatus()).isEqualTo(204);
        assertThat(outcome.links()).as("liens envoyés à l'ancienne adresse").isEmpty();
    }
}
