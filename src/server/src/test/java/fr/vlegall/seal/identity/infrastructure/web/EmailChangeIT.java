package fr.vlegall.seal.identity.infrastructure.web;

import static fr.vlegall.seal.support.AuthClient.PASSWORD;
import static fr.vlegall.seal.support.AuthClient.sessionOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.identity.domain.model.VerificationCode;
import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.Race;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** ACC-02, ADR 0034 : changement du courriel d'un compte actif, de bout en bout dans le contexte complet. */
@SealIntegrationTest
class EmailChangeIT {

    private static final String REQUEST = "/api/v1/account/email";
    private static final String CONFIRM = "/api/v1/account/email/confirm";
    private static final Pattern CODE = Pattern.compile("code de vérification SEAL : ([0-9]{6})\\.");
    private static final Pattern LINK =
            Pattern.compile("(?m)^https://\\S+/mot-de-passe/nouveau#jeton=([A-Za-z0-9_-]{43})$");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private MutableClock clock;

    private AuthClient client;

    /** Suffixe propre à ce test : la base est partagée par toute la série des tests d'intégration. */
    private final String run = UUID.randomUUID().toString().substring(0, 6);

    private String name(String base) {
        return base + run;
    }

    private String email(String base) {
        return base + run + "@exemple.ca";
    }

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    @AfterEach
    void reset() {
        mail.reset();
        clock.reset();
    }

    private Cookie active(String email, String username) throws Exception {
        Cookie full = client.verify(client.register(email, username));
        mail.reset();
        return full;
    }

    private String codeSentTo(String address) {
        List<SimpleMailMessage> sent = mail.sentTo(address);
        assertThat(sent).as("courriel reçu à " + address).isNotEmpty();
        Matcher matcher = CODE.matcher(sent.getLast().getText());
        assertThat(matcher.find()).as("code dans le courriel").isTrue();
        return matcher.group(1);
    }

    private String storedEmail(String username) {
        return jdbc.sql("SELECT email FROM users WHERE username = :username")
                .param("username", username)
                .query(String.class)
                .single();
    }

    private List<String> journal(UUID user) {
        return jdbc.sql("SELECT type FROM security_events WHERE user_id = :user ORDER BY id")
                .param("user", user)
                .query(String.class)
                .list();
    }

    private UUID idOf(String username) {
        return jdbc.sql("SELECT id FROM users WHERE username = :username")
                .param("username", username)
                .query(UUID.class)
                .single();
    }

    @Test
    @DisplayName(
            "ACC-02 : code au nouveau courriel, confirmation, compte courant rendu, ancienne adresse prévenue, EMAIL_CHANGED")
    void acc02ChangesTheAddress() throws Exception {
        String target = "Nouvelle" + run + "@Exemple.ca";
        Cookie full = active(email("ancienne"), name("change"));

        mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", target))).andExpect(status().isNoContent());
        assertThat(storedEmail(name("change"))).isEqualTo(email("ancienne"));
        SimpleMailMessage code = mail.sentTo(target).getLast();
        assertThat(code.getSubject()).isEqualTo("Votre code de vérification SEAL");
        String digits = codeSentTo(target);
        assertThat(code.getText())
                .isEqualTo("Votre code de vérification SEAL : " + digits + ". Il expire dans 10 minutes. Il confirme"
                        + " le changement de courriel du compte " + name("change")
                        + ". Si vous n'avez rien demandé, ignorez ce"
                        + " message.");

        mvc.perform(client.unsafe(post(CONFIRM), full, Map.of("code", digits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(target))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(storedEmail(name("change"))).isEqualTo(target);
        assertThat(journal(idOf(name("change")))).contains("EMAIL_CHANGED");
        SimpleMailMessage notice = mail.sentTo(email("ancienne")).getLast();
        assertThat(notice.getSubject()).isEqualTo("Votre courriel SEAL a été changé");
        assertThat(notice.getText())
                .isEqualTo("Le courriel de votre compte SEAL (identifiant : " + name("change")
                        + ") est désormais N***@Exemple.ca."
                        + " Si ce n'est pas vous, connectez-vous et changez votre mot de passe.");
        mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(
                                post("/api/v1/auth/login"),
                                null,
                                Map.of("login", target.toLowerCase(Locale.ROOT), "password", PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName(
            "AUTH-55 : facteur de plus de 10 minutes, 403 REAUTH_REQUIRED sans consommer d'envoi ; après /reauth, la demande passe")
    void auth55RequestNeedsSudo() throws Exception {
        Cookie full = active(email("sudo"), name("sudoemail"));
        clock.advance(Duration.ofMinutes(10));

        for (int i = 0; i < 4; i++) {
            mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("autre"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("REAUTH_REQUIRED"));
        }
        assertThat(mail.sentTo(email("autre"))).isEmpty();

        MvcResult reauth = mvc.perform(client.unsafe(post("/api/v1/auth/reauth"), full, Map.of("password", PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();
        mvc.perform(client.unsafe(post(REQUEST), sessionOf(reauth), Map.of("email", email("autre"))))
                .andExpect(status().isNoContent());
        assertThat(mail.sentTo(email("autre"))).hasSize(1);
    }

    @Test
    @DisplayName("ACC-02, AUTH-15, ADR 0034 : adresse prise, même 204, aucun code ; son titulaire est averti")
    void acc02TakenAddressRevealsNothing() throws Exception {
        active(email("pris"), name("titulaire"));
        Cookie full = new AuthClient(mvc, mail).verify(new AuthClient(mvc, mail).register(email("moi"), name("moi")));
        mail.reset();

        mvc.perform(client.unsafe(
                        post(REQUEST), full, Map.of("email", email("pris").toUpperCase(Locale.ROOT))))
                .andExpect(status().isNoContent());

        List<SimpleMailMessage> sent = mail.sentTo(email("pris").toUpperCase(Locale.ROOT));
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getSubject()).isEqualTo("Tentative d'ajout de votre adresse sur SEAL");
        assertThat(sent.getFirst().getText())
                .isEqualTo("Quelqu'un a tenté d'associer cette adresse à un autre compte SEAL. Elle reste associée"
                        + " à votre compte, qui n'est pas modifié.");
        assertThat(sent.getFirst().getText()).doesNotContainPattern("[0-9]{6}");
        assertThat(storedEmail(name("moi"))).isEqualTo(email("moi"));
    }

    @Test
    @DisplayName("ACC-02 : un mauvais code répond 422 INVALID_CODE ; après 5, même le bon répond CODE_EXPIRED")
    void acc02FiveWrongCodesExhaustTheCode() throws Exception {
        Cookie full = active(email("essais"), name("essais"));
        mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("cible"))))
                .andExpect(status().isNoContent());
        String code = codeSentTo(email("cible"));
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            mvc.perform(client.unsafe(post(CONFIRM), full, Map.of("code", wrong)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }
        mvc.perform(client.unsafe(post(CONFIRM), full, Map.of("code", code)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        assertThat(storedEmail(name("essais"))).isEqualTo(email("essais"));
    }

    @Test
    @DisplayName(
            "ACC-02, AUTH-15 : un autre compte active l'adresse entre la demande et la confirmation, 422 CODE_EXPIRED")
    void acc02AddressActivatedMeanwhile() throws Exception {
        Cookie full = active(email("lent"), name("lent"));
        mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("convoitee"))))
                .andExpect(status().isNoContent());
        String code = codeSentTo(email("convoitee"));

        AuthClient other = new AuthClient(mvc, mail);
        other.verify(other.register(email("convoitee"), name("rapide")));

        mvc.perform(client.unsafe(post(CONFIRM), full, Map.of("code", code)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        assertThat(storedEmail(name("lent"))).isEqualTo(email("lent"));
    }

    @Test
    @DisplayName("SEC-10, ADR 0034 (point 4) : 3 demandes par heure et par compte, puis 429 avec Retry-After")
    void sec10ThreeRequestsPerHour() throws Exception {
        Cookie full = active(email("limite"), name("limiteemail"));

        for (int i = 0; i < 3; i++) {
            mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("cible" + i))))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("cible3"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        assertThat(mail.sentTo(email("cible3"))).isEmpty();
    }

    @Test
    @DisplayName("ACC-02, ADR 0034 : un lien de réinitialisation envoyé à l'ancienne adresse ne sert plus")
    void acc02ChangeKillsLiveResetLinks() throws Exception {
        Cookie full = active(email("liens"), name("liens"));
        mvc.perform(new AuthClient(mvc, mail)
                        .unsafe(post("/api/v1/auth/password-reset/request"), null, Map.of("email", email("liens"))))
                .andExpect(status().isNoContent());
        Matcher link = LINK.matcher(mail.sentTo(email("liens")).getLast().getText());
        assertThat(link.find()).isTrue();

        mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("neuve"))))
                .andExpect(status().isNoContent());
        mvc.perform(client.unsafe(post(CONFIRM), full, Map.of("code", codeSentTo(email("neuve")))))
                .andExpect(status().isOk());

        mvc.perform(client.unsafe(post("/api/v1/auth/password-reset/inspect"), null, Map.of("token", link.group(1))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    @DisplayName("ACC-02 : un code de vérification du courriel ne confirme jamais un changement (usage distinct)")
    void acc02VerificationCodeNeverConfirmsAChange() throws Exception {
        Cookie full = active(email("usage"), name("usage"));
        jdbc.sql("INSERT INTO email_codes (user_id, purpose, code_hash, expires_at) "
                        + "VALUES (:user, 'VERIFY_EMAIL', :hash, :expiresAt)")
                .param("user", idOf(name("usage")))
                .param("hash", VerificationCode.parse("123456").orElseThrow().hash())
                .param(
                        "expiresAt",
                        OffsetDateTime.ofInstant(clock.instant().plus(Duration.ofMinutes(10)), ZoneOffset.UTC))
                .update();

        mvc.perform(client.unsafe(post(CONFIRM), full, Map.of("code", "123456")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
        assertThat(storedEmail(name("usage"))).isEqualTo(email("usage"));
    }

    @Test
    @DisplayName("ACC-02, ADR 0034 : une demande refusée sans rien envoyer ne consomme aucun des 3 envois de l'heure")
    void acc02RefusedRequestSpendsNoSend() throws Exception {
        Cookie full = active(email("rendu"), name("rendu"));

        for (int i = 0; i < 3; i++) {
            mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("rendu"))))
                    .andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 3; i++) {
            mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("cible" + i))))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("ACC-02, A06 : deux confirmations simultanées du bon code, une seule change l'adresse")
    void acc02ConcurrentConfirmationsSucceedOnce() throws Exception {
        Cookie full = active(email("double"), name("double"));
        mvc.perform(client.unsafe(post(REQUEST), full, Map.of("email", email("doublecible"))))
                .andExpect(status().isNoContent());
        String code = codeSentTo(email("doublecible"));
        var first = client.unsafe(post(CONFIRM), full, Map.of("code", code));
        var second = client.unsafe(post(CONFIRM), full, Map.of("code", code));

        List<Integer> statuses = Race.run(
                2,
                index -> mvc.perform(index == 0 ? first : second)
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(200, 422);
        assertThat(journal(idOf(name("double"))).stream().filter("EMAIL_CHANGED"::equals))
                .hasSize(1);
    }

    @Test
    @DisplayName("ACC-02 : l'adresse actuelle n'est pas un changement, 400 VALIDATION_FAILED")
    void acc02SameAddressIsRefused() throws Exception {
        Cookie full = active(email("meme"), name("meme"));

        mvc.perform(client.unsafe(
                        post(REQUEST), full, Map.of("email", email("meme").toUpperCase(Locale.ROOT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields[0]").value("email"));
        assertThat(mail.sentTo(email("meme").toUpperCase(Locale.ROOT))).isEmpty();
    }

    @Test
    @DisplayName(
            "QA-05 : 401 sans session, 403 sans jeton CSRF, 403 en session restreinte, 400 pour une saisie invalide")
    void qa05AccessRulesAndValidation() throws Exception {
        Registered pending = client.register(email("regles"), name("reglesemail"));

        for (String path : List.of(REQUEST, CONFIRM)) {
            Map<String, String> body =
                    path.equals(REQUEST) ? Map.of("email", "x@exemple.ca") : Map.of("code", "123456");
            mvc.perform(client.unsafe(post(path), null, body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(client.unsafe(post(path), pending.session(), body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        Cookie full = client.verify(pending);
        for (String path : List.of(REQUEST, CONFIRM)) {
            String body = path.equals(REQUEST) ? "{\"email\":\"x@exemple.ca\"}" : "{\"code\":\"123456\"}";
            mvc.perform(client.from(post(path))
                            .cookie(full)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        for (Map<String, String> invalid : List.of(
                Map.of("email", "pas-un-courriel"),
                Map.of("email", "x@exemple.ca", "username", "autre"),
                Map.of("email", "a".repeat(250) + "@exemple.ca"))) {
            mvc.perform(client.unsafe(post(REQUEST), full, invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        for (Map<String, String> invalid : List.<Map<String, String>>of(
                Map.of("code", "12345"),
                Map.of("code", "1234567"),
                Map.of("code", "abcdef"),
                Map.of("code", "123456", "email", "x@exemple.ca"),
                Map.of())) {
            mvc.perform(client.unsafe(post(CONFIRM), full, invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }
}
