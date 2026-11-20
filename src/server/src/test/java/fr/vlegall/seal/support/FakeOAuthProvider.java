package fr.vlegall.seal.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Faux fournisseur OAuth des tests (ADR 0031, point 4) : le {@code HttpServer} du JDK, sur un port libre de la boucle
 * locale, qui joue Google (OIDC, jeton d'identité signé en RS256 par Nimbus) et GitHub (OAuth 2.0, {@code /user} et
 * {@code /user/emails}). Le navigateur n'existe pas dans MockMvc : {@link #authorize} lit la redirection de SEAL et rend
 * le code que le navigateur rapporterait. Le point {@code /token} vérifie le secret du client, la redirection, l'usage
 * unique du code et {@code code_verifier} contre le défi PKCE.
 */
public final class FakeOAuthProvider implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.shared();

    /** Un compte Google ; {@code email} et {@code emailVerified} sont bruts, pour servir aussi des valeurs mal typées. */
    public record GoogleAccount(
            String sub,
            @Nullable Object email,
            @Nullable Object emailVerified,
            @Nullable String name) {

        public static GoogleAccount verified(String sub, String email) {
            return new GoogleAccount(sub, email, true, "Valentin Dupont");
        }
    }

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
            @Nullable List<GitHubEmail> emails) {

        public static GitHubAccount verified(long id, String email) {
            return new GitHubAccount(id, "octo" + id, "Octo Cat", List.of(new GitHubEmail(email, true, true)));
        }
    }

    /** Un consentement donné : ce que le code échangé devra prouver. */
    private record Grant(
            String clientId,
            String redirectUri,
            String challenge,
            @Nullable String nonce,
            Object account) {}

    private final HttpServer server;
    private final URI base;
    private final RSAKey signingKey;
    private final RSAKey strangerKey;
    private final Map<String, String> clientSecrets = new ConcurrentHashMap<>();
    private final Map<String, Grant> codes = new ConcurrentHashMap<>();
    private final Map<String, GoogleAccount> googleTokens = new ConcurrentHashMap<>();
    private final Map<String, GitHubAccount> githubAccounts = new ConcurrentHashMap<>();
    private volatile UnaryOperator<JWTClaimsSet.Builder> idTokenTweak = UnaryOperator.identity();
    private volatile boolean signWithStranger;

    private FakeOAuthProvider(HttpServer server) {
        this.server = server;
        this.base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        try {
            this.signingKey = new RSAKeyGenerator(2048).keyID("fake").generate();
            this.strangerKey = new RSAKeyGenerator(2048).keyID("fake").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/google/token", exchange -> token(exchange, true));
        server.createContext("/github/token", exchange -> token(exchange, false));
        server.createContext("/google/jwks", this::jwks);
        server.createContext("/google/userinfo", this::googleUserInfo);
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

    /** Le secret attendu de ce client au point {@code /token}. */
    public void client(String clientId, String clientSecret) {
        clientSecrets.put(clientId, clientSecret);
    }

    /** Le compte GitHub que servent {@code /user} et {@code /user/emails} à ce jeton d'accès. */
    public void githubAccount(String accessToken, GitHubAccount account) {
        githubAccounts.put(accessToken, account);
    }

    /**
     * Le consentement du compte donné à la redirection de SEAL : le code que le navigateur rapporterait. La redirection
     * doit porter PKCE en S256.
     */
    public String authorize(String location, Object account) {
        return authorize(location, account, null);
    }

    /** Comme {@link #authorize}, mais le code est lié à un autre défi PKCE : {@code code_verifier} ne collera pas. */
    public String authorizeWithOtherChallenge(String location, Object account) {
        return authorize(location, account, "autre-defi-que-celui-de-seal");
    }

    private String authorize(String location, Object account, @Nullable String challenge) {
        Map<String, String> query = query(location);
        if (!"S256".equals(query.get("code_challenge_method"))) {
            throw new IllegalStateException("redirection sans PKCE S256 : " + location);
        }
        String code = UUID.randomUUID().toString();
        codes.put(
                code,
                new Grant(
                        query.get("client_id"),
                        query.get("redirect_uri"),
                        challenge == null ? query.get("code_challenge") : challenge,
                        query.get("nonce"),
                        account));
        return code;
    }

    /** Modifie les déclarations des prochains jetons d'identité (audience, émetteur, nonce, dates). */
    public void tweakIdTokens(UnaryOperator<JWTClaimsSet.Builder> tweak) {
        this.idTokenTweak = tweak;
    }

    /** Signe les prochains jetons d'identité avec une clé absente des JWKS, sous le même {@code kid}. */
    public void signWithStrangerKey(boolean stranger) {
        this.signWithStranger = stranger;
    }

    /** Rétablit des jetons d'identité normaux. */
    public void reset() {
        idTokenTweak = UnaryOperator.identity();
        signWithStranger = false;
    }

    /** Paramètres décodés d'une adresse. */
    public static Map<String, String> query(String location) {
        Map<String, String> query = new LinkedHashMap<>();
        UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .toSingleValueMap()
                .forEach((key, value) -> query.put(key, URLDecoder.decode(value, StandardCharsets.UTF_8)));
        return query;
    }

    private void token(HttpExchange exchange, boolean google) throws IOException {
        Map<String, String> form = form(exchange);
        Grant grant = codes.remove(form.getOrDefault("code", ""));
        if (grant == null
                || !"authorization_code".equals(form.get("grant_type"))
                || !grant.redirectUri().equals(form.get("redirect_uri"))
                || !clientAuthenticated(exchange, grant.clientId())
                || !grant.challenge().equals(s256(form.get("code_verifier")))) {
            respond(exchange, 400, Map.of("error", "invalid_grant"));
            return;
        }
        String accessToken = UUID.randomUUID().toString();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", 3600);
        if (google && grant.account() instanceof GoogleAccount account) {
            googleTokens.put(accessToken, account);
            response.put("scope", "openid email profile");
            response.put("id_token", idToken(account, grant));
        } else if (!google && grant.account() instanceof GitHubAccount account) {
            githubAccounts.put(accessToken, account);
            response.put("scope", "read:user,user:email");
        } else {
            respond(exchange, 400, Map.of("error", "invalid_grant"));
            return;
        }
        respond(exchange, 200, response);
    }

    private String idToken(GoogleAccount account, Grant grant) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(uri("/google"))
                .subject(account.sub())
                .audience(grant.clientId())
                .claim("azp", grant.clientId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("nonce", grant.nonce())
                .claim("email", account.email())
                .claim("email_verified", account.emailVerified())
                .claim("name", account.name());
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(signingKey.getKeyID())
                        .build(),
                idTokenTweak.apply(claims).build());
        try {
            jwt.sign(new RSASSASigner(signWithStranger ? strangerKey : signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private void jwks(HttpExchange exchange) throws IOException {
        respond(exchange, 200, Map.of("keys", List.of(signingKey.toPublicJWK().toJSONObject())));
    }

    private void googleUserInfo(HttpExchange exchange) throws IOException {
        GoogleAccount account = googleTokens.get(String.valueOf(bearer(exchange)));
        if (account == null) {
            respond(exchange, 401, Map.of("error", "invalid_token"));
            return;
        }
        Map<String, @Nullable Object> user = new LinkedHashMap<>();
        user.put("sub", account.sub());
        user.put("email", account.email());
        user.put("email_verified", account.emailVerified());
        user.put("name", account.name());
        user.values().removeIf(value -> value == null);
        respond(exchange, 200, user);
    }

    private void githubUser(HttpExchange exchange) throws IOException {
        GitHubAccount account = githubAccounts.get(String.valueOf(bearer(exchange)));
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
        GitHubAccount account = githubAccounts.get(String.valueOf(bearer(exchange)));
        if (account == null) {
            respond(exchange, 401, Map.of("message", "Bad credentials"));
        } else if (account.emails() == null) {
            respond(exchange, 500, Map.of("message", "Server Error"));
        } else {
            respond(exchange, 200, account.emails());
        }
    }

    /** client_secret_basic : identifiant et secret encodés en URL, puis en base64 (RFC 6749, section 2.3.1). */
    private boolean clientAuthenticated(HttpExchange exchange, String clientId) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return false;
        }
        String decoded = new String(
                Base64.getDecoder().decode(authorization.substring("Basic ".length())), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        if (colon < 0) {
            return false;
        }
        String id = URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
        String secret = URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8);
        return id.equals(clientId) && secret.equals(clientSecrets.get(clientId));
    }

    private static String s256(@Nullable String verifier) {
        if (verifier == null) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static @Nullable String bearer(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                form.put(
                        URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
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
