package fr.vlegall.seal.conversations.infrastructure.web;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Compte vérifié, en session complète, pour les tests des conversations. */
final class ConversationsTestClient {

    static final String CONVERSATIONS = "/api/v1/conversations";

    final AuthClient client;
    final UUID id;
    final Cookie session;

    private ConversationsTestClient(AuthClient client, UUID id, Cookie session) {
        this.client = client;
        this.id = id;
        this.session = session;
    }

    static ConversationsTestClient fresh(
            org.springframework.test.web.servlet.MockMvc mvc, RecordingMailSender mail, String prefix)
            throws Exception {
        String username = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered registered = client.register(username + "@exemple.ca", username);
        return new ConversationsTestClient(client, registered.id(), client.verify(registered));
    }

    /** Amitié écrite directement en base, sans passer par une invitation. */
    static void befriend(JdbcClient jdbc, ConversationsTestClient a, ConversationsTestClient b) {
        jdbc.sql("INSERT INTO friendships (user_low, user_high) VALUES (LEAST(:a, :b), GREATEST(:a, :b))")
                .param("a", a.id)
                .param("b", b.id)
                .update();
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
}
