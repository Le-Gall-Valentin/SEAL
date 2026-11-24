package fr.vlegall.seal.friends.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.TestAccount;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
    private final TestAccount account;

    private FriendsTestClient(MockMvc mvc, TestAccount account) {
        this.mvc = mvc;
        this.account = account;
        this.client = account.client;
        this.id = account.id;
        this.username = account.username;
        this.session = account.session;
    }

    /** Identifiant neuf, au format d'AUTH-01. */
    static String name(String prefix) {
        return TestAccount.name(prefix);
    }

    /** Compte neuf dont l'identifiant commence par {@code prefix}. */
    static FriendsTestClient fresh(MockMvc mvc, RecordingMailSender mail, String prefix) throws Exception {
        return new FriendsTestClient(mvc, TestAccount.fresh(mvc, mail, prefix));
    }

    static FriendsTestClient account(MockMvc mvc, RecordingMailSender mail, String username) throws Exception {
        return new FriendsTestClient(mvc, TestAccount.register(mvc, mail, username));
    }

    /** Requête non sûre de ce compte, jeton CSRF compris. */
    MockHttpServletRequestBuilder unsafe(MockHttpServletRequestBuilder request, @Nullable Map<String, ?> body)
            throws Exception {
        return account.unsafe(request, body);
    }

    /** Requête sûre de ce compte. */
    MockHttpServletRequestBuilder safe(MockHttpServletRequestBuilder request) {
        return account.safe(request);
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
