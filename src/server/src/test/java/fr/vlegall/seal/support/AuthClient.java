package fr.vlegall.seal.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Client HTTP des tests d'intégration de l'authentification : une adresse IP par client, pour que les seaux de
 * limitation de débit, qui vivent le temps du contexte, ne se mélangent pas entre tests.
 */
public final class AuthClient {

    public static final String SESSION_COOKIE = "__Host-seal_sid";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";
    public static final String PASSWORD = "fjord-tuque-raquette";

    private static final Pattern CODE = Pattern.compile("code de vérification SEAL : ([0-9]{6})\\.");
    private static final JsonMapper JSON = JsonMapper.shared();
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private final MockMvc mvc;
    private final RecordingMailSender mail;
    private final String ip;

    public AuthClient(MockMvc mvc, RecordingMailSender mail) {
        this.mvc = mvc;
        this.mail = mail;
        int next = NEXT_IP.getAndIncrement();
        this.ip = "10.%d.%d.%d".formatted(next >> 16 & 0xFF, next >> 8 & 0xFF, next & 0xFF);
    }

    /** Compte inscrit et sa session restreinte. */
    public record Registered(UUID id, String email, String username, Cookie session) {}

    public String ip() {
        return ip;
    }

    public MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request) {
        return request.secure(true).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }

    /** Jeton CSRF frais, pour la session donnée ou pour un anonyme (AUTH-60). */
    public String csrfToken(@Nullable Cookie session) throws Exception {
        MockHttpServletRequestBuilder request = from(get("/api/v1/csrf"));
        if (session != null) {
            request.cookie(session);
        }
        Cookie cookie = mvc.perform(request).andReturn().getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).as("jeton CSRF").isNotNull();
        return cookie.getValue();
    }

    /** Requête non sûre avec son jeton CSRF et, s'il y a lieu, la session. */
    public MockHttpServletRequestBuilder unsafe(
            MockHttpServletRequestBuilder request, @Nullable Cookie session, @Nullable Map<String, ?> body)
            throws Exception {
        String token = csrfToken(session);
        MockHttpServletRequestBuilder built =
                from(request).cookie(new Cookie(CSRF_COOKIE, token)).header(CSRF_HEADER, token);
        if (session != null) {
            built.cookie(session);
        }
        if (body != null) {
            built.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        return built;
    }

    public static Map<String, Object> registration(String email, String username) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("username", username);
        body.put("displayName", "Valentin");
        body.put("password", PASSWORD);
        return body;
    }

    public Registered register(String email, String username) throws Exception {
        MvcResult result = mvc.perform(unsafe(post("/api/v1/auth/register"), null, registration(email, username)))
                .andExpect(status().isCreated())
                .andReturn();
        return new Registered(UUID.fromString(body(result).get("id").asString()), email, username, sessionOf(result));
    }

    /** Dernier code reçu à cette adresse ; les courriels partent avant la réponse ({@code MailTestConfiguration}). */
    public String codeSentTo(String address) {
        List<SimpleMailMessage> received = mail.sentTo(address);
        assertThat(received).as("courriel reçu à " + address).isNotEmpty();
        SimpleMailMessage message = received.getLast();
        Matcher matcher = CODE.matcher(message.getText() == null ? "" : message.getText());
        assertThat(matcher.find()).as("code dans le courriel").isTrue();
        return matcher.group(1);
    }

    /** Vérifie le courriel et renvoie la nouvelle session, complète. */
    public Cookie verify(Registered account) throws Exception {
        MvcResult result = mvc.perform(unsafe(
                        post("/api/v1/auth/verify-email"),
                        account.session(),
                        Map.of("code", codeSentTo(account.email()))))
                .andExpect(status().isOk())
                .andReturn();
        return sessionOf(result);
    }

    public static Cookie sessionOf(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("cookie de session").isNotNull();
        return cookie;
    }

    public static JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
