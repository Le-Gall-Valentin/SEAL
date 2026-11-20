package fr.vlegall.seal.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Faux fournisseur OAuth des tests (ADR 0031, point 4) : le {@code HttpServer} du JDK, sur un port libre de la boucle
 * locale. Le navigateur n'existe pas dans MockMvc : le test lit la redirection vers {@code /authorize}, puis revient
 * lui-même sur {@code /login/oauth2/code/{registrationId}}.
 */
public final class FakeOAuthProvider implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.shared();

    /** Une adresse de {@code /user/emails} de GitHub. */
    public record GitHubEmail(String email, boolean primary, boolean verified) {}

    /**
     * Un compte GitHub, tel que le servent {@code /user} et {@code /user/emails}.
     *
     * @param id valeur brute de {@code id}, pour servir aussi un id mal typé
     * @param emails adresses du compte ; {@code null} fait répondre 500 à {@code /user/emails}
     */
    public record GitHubAccount(
            Object id,
            String login,
            @Nullable String name,
            @Nullable List<GitHubEmail> emails) {}

    private final HttpServer server;
    private final URI base;
    private final Map<String, GitHubAccount> githubAccounts = new ConcurrentHashMap<>();

    private FakeOAuthProvider(HttpServer server) {
        this.server = server;
        this.base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.createContext("/github/user", this::githubUser);
        server.createContext("/github/user/emails", this::githubEmails);
    }

    public static FakeOAuthProvider start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeOAuthProvider provider = new FakeOAuthProvider(server);
            server.start();
            return provider;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Adresse d'un point de ce fournisseur. */
    public String uri(String path) {
        return base.resolve(path).toString();
    }

    /** L'inscription Google de production, dont les points d'accès désignent ce faux fournisseur. */
    public ClientRegistration.Builder google(ClientRegistration.Builder production) {
        return production
                .authorizationUri(uri("/google/authorize"))
                .tokenUri(uri("/google/token"))
                .userInfoUri(uri("/google/userinfo"))
                .jwkSetUri(uri("/google/jwks"))
                .issuerUri(uri("/google"));
    }

    /** L'inscription GitHub de production, dont les points d'accès désignent ce faux fournisseur. */
    public ClientRegistration.Builder github(ClientRegistration.Builder production) {
        return production
                .authorizationUri(uri("/github/authorize"))
                .tokenUri(uri("/github/token"))
                .userInfoUri(uri("/github/user"));
    }

    /** Le compte GitHub que servent {@code /user} et {@code /user/emails} à ce jeton d'accès. */
    public void githubAccount(String accessToken, GitHubAccount account) {
        githubAccounts.put(accessToken, account);
    }

    private void githubUser(HttpExchange exchange) throws IOException {
        GitHubAccount account = githubAccountOf(exchange);
        if (account == null) {
            respond(exchange, 401, Map.of("message", "Bad credentials"));
            return;
        }
        Map<String, @Nullable Object> user = new LinkedHashMap<>();
        user.put("id", account.id());
        user.put("login", account.login());
        user.put("name", account.name());
        respond(exchange, 200, user);
    }

    private void githubEmails(HttpExchange exchange) throws IOException {
        GitHubAccount account = githubAccountOf(exchange);
        if (account == null) {
            respond(exchange, 401, Map.of("message", "Bad credentials"));
        } else if (account.emails() == null) {
            respond(exchange, 500, Map.of("message", "Server Error"));
        } else {
            respond(exchange, 200, account.emails());
        }
    }

    private @Nullable GitHubAccount githubAccountOf(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return githubAccounts.get(authorization.substring("Bearer ".length()));
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
