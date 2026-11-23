package fr.vlegall.seal.friends.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Compte vérifié, en session complète, pour les tests des amis. */
final class FriendsTestClient {

    static final String REQUESTS = "/api/v1/friend-requests";
    static final String FRIENDS = "/api/v1/friends";

    final AuthClient client;
    final UUID id;
    final String username;
    final Cookie session;

    private final MockMvc mvc;

    private FriendsTestClient(MockMvc mvc, AuthClient client, UUID id, String username, Cookie session) {
        this.mvc = mvc;
        this.client = client;
        this.id = id;
        this.username = username;
        this.session = session;
    }

    /** Identifiant neuf, au format d'AUTH-01. */
    static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** Compte neuf dont l'identifiant commence par {@code prefix}. */
    static FriendsTestClient fresh(MockMvc mvc, RecordingMailSender mail, String prefix) throws Exception {
        return account(mvc, mail, name(prefix));
    }

    /** Amitié écrite directement en base, sans passer par une invitation. */
    static void befriend(JdbcClient jdbc, UUID a, UUID b) {
        jdbc.sql("INSERT INTO friendships (user_low, user_high) VALUES (LEAST(:a, :b), GREATEST(:a, :b))")
                .param("a", a)
                .param("b", b)
                .update();
    }

    /** Compte supprimé (ACC-07), donc plus actif. */
    static void deactivate(JdbcClient jdbc, UUID user) {
        jdbc.sql("UPDATE users SET status = 'DELETED', email = NULL, deleted_at = now() WHERE id = ?")
                .param(user)
                .update();
    }

    static FriendsTestClient account(MockMvc mvc, RecordingMailSender mail, String username) throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered registered = client.register(username + "@exemple.ca", username);
        return new FriendsTestClient(mvc, client, registered.id(), username, client.verify(registered));
    }

    /** Requête non sûre de ce compte, jeton CSRF compris. */
    MockHttpServletRequestBuilder unsafe(MockHttpServletRequestBuilder request, @Nullable Map<String, ?> body)
            throws Exception {
        return client.unsafe(request, session, body);
    }

    /** Requête sûre de ce compte. */
    MockHttpServletRequestBuilder safe(MockHttpServletRequestBuilder request) {
        return client.from(request).cookie(session);
    }

    /** Invite {@code receiver}, attend 201 et rend l'identifiant de l'invitation. */
    UUID inviteOk(FriendsTestClient receiver) throws Exception {
        return UUID.fromString(AuthClient.body(invite(receiver.username)
                        .andExpect(status().isCreated())
                        .andReturn())
                .get("id")
                .asString());
    }

    /** {@code POST /friend-requests} avec cette saisie. */
    ResultActions invite(String username) throws Exception {
        return mvc.perform(unsafe(post(REQUESTS), Map.of("username", username)));
    }
}
