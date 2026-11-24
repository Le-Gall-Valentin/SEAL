package fr.vlegall.seal.support;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Compte inscrit et vérifié, en session complète : la base des clients de test de chaque contexte, qui la composent
 * avec leurs propres requêtes.
 */
public final class TestAccount {

    public final AuthClient client;
    public final UUID id;
    public final String username;
    public final Cookie session;

    private TestAccount(AuthClient client, UUID id, String username, Cookie session) {
        this.client = client;
        this.id = id;
        this.username = username;
        this.session = session;
    }

    /** Identifiant neuf, au format d'AUTH-01. */
    public static String name(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /** Compte neuf dont l'identifiant commence par {@code prefix}. */
    public static TestAccount fresh(MockMvc mvc, RecordingMailSender mail, String prefix) throws Exception {
        return register(mvc, mail, name(prefix));
    }

    /** Compte neuf à cet identifiant exact. */
    public static TestAccount register(MockMvc mvc, RecordingMailSender mail, String username) throws Exception {
        AuthClient client = new AuthClient(mvc, mail);
        AuthClient.Registered registered = client.register(username + "@exemple.ca", username);
        return new TestAccount(client, registered.id(), username, client.verify(registered));
    }

    /** Requête non sûre de ce compte, jeton CSRF compris. */
    public MockHttpServletRequestBuilder unsafe(MockHttpServletRequestBuilder request, @Nullable Map<String, ?> body)
            throws Exception {
        return client.unsafe(request, session, body);
    }

    /** Requête sûre de ce compte. */
    public MockHttpServletRequestBuilder safe(MockHttpServletRequestBuilder request) {
        return client.from(request).cookie(session);
    }
}
