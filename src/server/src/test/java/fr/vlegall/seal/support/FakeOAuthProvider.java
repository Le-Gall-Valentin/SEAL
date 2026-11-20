package fr.vlegall.seal.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

/**
 * Faux fournisseur OAuth des tests d'intégration (ADR 0031, point 4) : le {@code HttpServer} du JDK, sur un port libre
 * de la boucle locale. Le navigateur n'existe pas dans MockMvc : le test lit la redirection vers {@code /authorize},
 * puis revient lui-même sur {@code /login/oauth2/code/{registrationId}}.
 */
public final class FakeOAuthProvider implements AutoCloseable {

    private final HttpServer server;
    private final URI base;

    private FakeOAuthProvider(HttpServer server) {
        this.server = server;
        this.base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public static FakeOAuthProvider start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.start();
            return new FakeOAuthProvider(server);
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

    @Override
    public void close() {
        server.stop(0);
    }
}
