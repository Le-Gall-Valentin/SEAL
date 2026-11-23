package fr.vlegall.seal.friends.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
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

    static FriendsTestClient account(MockMvc mvc, RecordingMailSender mail, String username) throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered registered = client.register(username + "@exemple.ca", username);
        return new FriendsTestClient(mvc, client, registered.id(), username, client.verify(registered));
    }

    /** Requête non sûre de ce compte, jeton CSRF compris. */
    MockHttpServletRequestBuilder unsafe(MockHttpServletRequestBuilder request, Map<String, ?> body) throws Exception {
        return client.unsafe(request, session, body);
    }

    /** Requête sûre de ce compte. */
    MockHttpServletRequestBuilder safe(MockHttpServletRequestBuilder request) {
        return client.from(request).cookie(session);
    }

    /** {@code POST /friend-requests} avec cette saisie. */
    ResultActions invite(String username) throws Exception {
        return mvc.perform(unsafe(post(REQUESTS), Map.of("username", username)));
    }
}
