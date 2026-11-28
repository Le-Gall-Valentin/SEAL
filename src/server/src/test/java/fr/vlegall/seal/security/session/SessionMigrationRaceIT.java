package fr.vlegall.seal.security.session;

import static fr.vlegall.seal.support.AuthClient.PASSWORD;
import static fr.vlegall.seal.support.AuthClient.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AUTH-23, ADR 0045 : une requête concurrente, qui a lu la session avant son renommage et l'écrit après, ne fait
 * revivre ni l'ancien identifiant ni ses droits. Le test rejoue cet ordre exact : lecture, renommage par une requête,
 * écriture.
 */
@SealIntegrationTest
class SessionMigrationRaceIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private SessionRepository<? extends Session> sessions;

    private AuthClient client;

    private final String run = UUID.randomUUID().toString().substring(0, 6);

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    @AfterEach
    void reset() {
        mail.reset();
        clock.reset();
    }

    /** Requête qui renomme la session et rend le nouveau cookie. */
    @FunctionalInterface
    private interface Renaming {
        Cookie rename(Cookie session) throws Exception;
    }

    private Cookie fullSession(String base) throws Exception {
        return client.verify(client.register(base + run + "@exemple.ca", base + run));
    }

    /** Courriel de la dernière inscription restreinte, où part le code de vérification. */
    private String email = "";

    private Cookie restrictedSession(String base) throws Exception {
        email = base + run + "@exemple.ca";
        return client.register(email, base + run).session();
    }

    private static String sessionId(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    private int meStatus(Cookie session) throws Exception {
        return mvc.perform(client.from(get("/api/v1/auth/me")).cookie(session))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String primaryId(String sessionId) {
        return jdbc.sql("SELECT PRIMARY_ID FROM SPRING_SESSION WHERE SESSION_ID = ?")
                .param(sessionId)
                .query(String.class)
                .single();
    }

    /**
     * La requête concurrente lit la session sous l'ancien identifiant, la requête qui renomme se termine, puis la
     * requête concurrente écrit sa copie, modifiée par {@code change}, comme Spring Session le fait à la fin de toute
     * requête.
     */
    private <S extends Session> Cookie interleave(
            SessionRepository<S> repository, Cookie session, Renaming renaming, Consumer<? super S> change)
            throws Exception {
        S stale = repository.findById(sessionId(session));
        assertThat(stale).as("session lue par la requête concurrente").isNotNull();
        Cookie renewed = renaming.rename(session);
        change.accept(stale);
        repository.save(stale);
        return renewed;
    }

    private Consumer<Session> touched() {
        return stale -> stale.setLastAccessedTime(clock.instant().plusSeconds(1));
    }

    private int emailChangeStatus(Cookie session) throws Exception {
        return mvc.perform(client.unsafe(
                        post("/api/v1/account/email"), session, Map.of("email", "neuve" + run + "@exemple.ca")))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    /** AUTH-16 : la vérification du courriel rend complète la session restreinte de l'inscription. */
    private Cookie verifyEmail(Cookie restricted) throws Exception {
        MvcResult result = mvc.perform(client.unsafe(
                        post("/api/v1/auth/verify-email"), restricted, Map.of("code", client.codeSentTo(email))))
                .andExpect(status().isOk())
                .andReturn();
        return sessionOf(result);
    }

    private Cookie reauthenticate(Cookie session) throws Exception {
        MvcResult result = mvc.perform(
                        client.unsafe(post("/api/v1/auth/reauth"), session, Map.of("password", PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        return sessionOf(result);
    }

    private Cookie changePassword(Cookie session) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentPassword", PASSWORD);
        body.put("newPassword", "raquette-sous-la-neige");
        MvcResult result = mvc.perform(client.unsafe(post("/api/v1/account/password"), session, body))
                .andExpect(status().isNoContent())
                .andReturn();
        return sessionOf(result);
    }

    @Test
    @DisplayName(
            "AUTH-23, AUTH-55, ADR 0045 : la ré-authentification crée une nouvelle session, sous un autre PRIMARY_ID")
    void auth23ReauthenticationMigratesTheSession() throws Exception {
        Cookie full = fullSession("migre");
        String before = primaryId(sessionId(full));

        Cookie renewed = reauthenticate(full);

        assertThat(primaryId(sessionId(renewed))).isNotEqualTo(before);
        assertThat(sessions.findById(sessionId(full))).isNull();
    }

    @Test
    @DisplayName(
            "AUTH-23, AUTH-55, ADR 0045 : une requête concurrente à la ré-authentification ne fait pas revivre l'ancien cookie")
    void auth23ConcurrentWriteDuringReauthenticationRevivesNothing() throws Exception {
        Cookie full = fullSession("course");

        clock.advance(Duration.ofMinutes(11));
        assertThat(emailChangeStatus(full)).as("hors du mode sudo").isEqualTo(403);

        Cookie renewed = interleave(sessions, full, this::reauthenticate, touched());

        assertThat(sessions.findById(sessionId(full))).as("ancien identifiant").isNull();
        assertThat(sessions.findById(sessionId(renewed)))
                .as("nouvel identifiant")
                .isNotNull();
        assertThat(meStatus(full)).isEqualTo(401);
        assertThat(emailChangeStatus(full)).as("ancien cookie").isEqualTo(401);
        assertThat(emailChangeStatus(renewed)).as("mode sudo du nouveau cookie").isEqualTo(204);
    }

    @Test
    @DisplayName(
            "AUTH-23, ACC-01, ADR 0045 : une requête concurrente au changement de mot de passe ne fait pas revivre l'ancien cookie")
    void auth23ConcurrentWriteDuringPasswordChangeRevivesNothing() throws Exception {
        Cookie full = fullSession("mdp");

        Cookie renewed = interleave(sessions, full, this::changePassword, touched());

        assertThat(sessions.findById(sessionId(full))).as("ancien identifiant").isNull();
        assertThat(meStatus(full)).isEqualTo(401);
        assertThat(meStatus(renewed)).isEqualTo(200);
    }

    @Test
    @DisplayName(
            "AUTH-23, ADR 0045 : l'attribut qu'ajoute une requête concurrente est perdu, sans faire revivre l'ancien cookie")
    void auth23ConcurrentAttributeWriteIsDropped() throws Exception {
        Cookie full = fullSession("attribut");

        // Spring Session JDBC ignore la clé étrangère violée : il suppose la session supprimée par une autre requête.
        interleave(sessions, full, this::reauthenticate, stale -> stale.setAttribute("seal.test.race", "x"));

        assertThat(sessions.findById(sessionId(full))).as("ancien identifiant").isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = ?")
                        .param("seal.test.race")
                        .query(Long.class)
                        .single())
                .as("attribut orphelin")
                .isZero();
        assertThat(meStatus(full)).isEqualTo(401);
    }

    @Test
    @DisplayName(
            "AUTH-23, AUTH-16, ADR 0045 : une requête concurrente à la vérification du courriel ne fait pas revivre la session restreinte")
    void auth23ConcurrentWriteDuringEmailVerificationRevivesNothing() throws Exception {
        Cookie restricted = restrictedSession("verif");

        Cookie full = interleave(sessions, restricted, this::verifyEmail, touched());

        assertThat(sessions.findById(sessionId(restricted)))
                .as("ancien identifiant")
                .isNull();
        assertThat(meStatus(restricted)).isEqualTo(401);
        assertThat(meStatus(full)).isEqualTo(200);
    }

    @Test
    @DisplayName(
            "AUTH-23, ADR 0045 : une requête concurrente qui ajoute et modifie des attributs échoue, sans faire revivre l'ancien cookie")
    void auth23ConcurrentMixedAttributeWriteRevivesNothing() throws Exception {
        Cookie full = fullSession("mixte");

        Throwable thrown = catchThrowable(() -> interleave(sessions, full, this::reauthenticate, stale -> {
            stale.setAttribute("seal.test.race", "x");
            stale.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    stale.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        }));
        // L'attribut ajouté viole la clé étrangère et avorte la transaction : la modification suivante échoue (échec
        // fermé).
        assertThat(thrown).isInstanceOf(DataAccessException.class);

        assertThat(sessions.findById(sessionId(full))).as("ancien identifiant").isNull();
        assertThat(meStatus(full)).isEqualTo(401);
    }
}
