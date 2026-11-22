package fr.vlegall.seal.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Parcours OAuth par MockMvc, contre le {@link FakeOAuthProvider} : départ, consentement simulé, retour avec la session
 * du départ.
 */
public final class OAuthFlow {

    private final MockMvc mvc;
    private final AuthClient client;
    private final FakeOAuthProvider fake;

    public OAuthFlow(MockMvc mvc, AuthClient client, FakeOAuthProvider fake) {
        this.mvc = mvc;
        this.client = client;
        this.fake = fake;
    }

    /** Un départ : la session qui garde la requête d'autorisation, et la redirection vers le fournisseur. */
    public record Started(String provider, Cookie session, String location) {

        public String state() {
            return FakeOAuthProvider.query(location).get("state");
        }
    }

    public Started start(String provider) throws Exception {
        MvcResult result = mvc.perform(client.from(get("/oauth2/authorization/" + provider)))
                .andReturn();
        return started(provider, result, AuthClient.sessionOf(result));
    }

    /** Un départ depuis une session SEAL ouverte : la ré-authentification (AUTH-55, ADR 0034). */
    public Started start(String provider, Cookie session) throws Exception {
        MvcResult result = mvc.perform(
                        client.from(get("/oauth2/authorization/" + provider)).cookie(session))
                .andReturn();
        // La session existe déjà : le départ ne pose pas de nouveau cookie.
        return started(provider, result, session);
    }

    private static Started started(String provider, MvcResult result, Cookie session) {
        assertThat(result.getResponse().getStatus())
                .as("départ vers " + provider)
                .isEqualTo(302);
        return new Started(provider, session, result.getResponse().getHeader("Location"));
    }

    /** Le retour du navigateur, avec ce code et le {@code state} du départ. */
    public MvcResult callback(Started started, String code) throws Exception {
        return callback(started, code, started.state(), started.session());
    }

    /** Le retour, avec un {@code state} et une session choisis par le test. */
    public MvcResult callback(Started started, String code, String state, Cookie session) throws Exception {
        return mvc.perform(client.from(get("/login/oauth2/code/" + started.provider())
                                .param("code", code)
                                .param("state", state))
                        .cookie(session))
                .andReturn();
    }

    /** Départ, consentement de ce compte, puis retour. */
    public MvcResult signIn(String provider, Object account) throws Exception {
        Started started = start(provider);
        return callback(started, fake.authorize(started.location(), account));
    }

    /** Paramètres décodés d'une adresse. */
    public static Map<String, String> query(String location) {
        return FakeOAuthProvider.query(location);
    }
}
