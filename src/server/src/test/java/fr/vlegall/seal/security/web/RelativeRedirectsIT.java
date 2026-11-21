package fr.vlegall.seal.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.TestcontainersConfiguration;
import fr.vlegall.seal.support.MutableClockConfiguration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * TRN-02, A02 : les redirections de SEAL restent relatives, sur l'origine unique, quel que soit l'en-tête
 * {@code Host}. Tomcat les rend sinon absolues à partir de cet en-tête, que choisit le client. MockMvc ne le montre
 * pas : ce test démarre le vrai serveur, sans TLS (profil test), et envoie la requête brute, puisque le client HTTP du
 * JDK interdit de fixer {@code Host}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class RelativeRedirectsIT {

    @LocalServerPort
    private int port;

    /** L'en-tête {@code Location} de la réponse à une requête GET envoyée avec cette version et cet en-tête {@code Host}. */
    private Optional<String> location(String version, String path, String host) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " " + version + "\r\nHost: " + host + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader in =
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
                if (line.toLowerCase(Locale.ROOT).startsWith("location:")) {
                    return Optional.of(line.substring("location:".length()).strip());
                }
            }
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("TRN-02, A02 : un retour OAuth refusé renvoie vers /connexion, sans l'hôte de l'en-tête Host")
    void a02OAuthRedirectIgnoresTheHostHeader() throws Exception {
        assertThat(location("HTTP/1.1", "/login/oauth2/code/google?code=a&state=b", "evil.example"))
                .contains("/connexion?erreur=REQUEST_REJECTED");
    }

    @Test
    @DisplayName("TRN-02, A02 : en HTTP/1.0, où Tomcat rend la redirection absolue, l'hôte reste celui de seal.origin")
    void a02Http10RedirectKeepsTheCanonicalHost() throws Exception {
        assertThat(location("HTTP/1.0", "/login/oauth2/code/google?code=a&state=b", "evil.example"))
                .hasValueSatisfying(location -> assertThat(URI.create(location))
                        .hasHost("localhost")
                        .hasPort(8443)
                        .hasPath("/connexion")
                        .hasQuery("erreur=REQUEST_REJECTED"));
        assertThat(location("HTTP/1.0", "http://evil.example/login/oauth2/code/google?code=a&state=b", "evil.example"))
                .hasValueSatisfying(location -> assertThat(URI.create(location)).hasHost("localhost"));
    }
}
