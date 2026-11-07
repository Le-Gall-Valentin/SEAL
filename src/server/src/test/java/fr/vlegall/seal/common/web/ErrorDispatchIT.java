package fr.vlegall.seal.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.TestcontainersConfiguration;
import fr.vlegall.seal.support.MutableClockConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Le dispatch d'erreur de Tomcat (sendError depuis un filtre) n'existe pas avec MockMvc : ce test démarre le vrai
 * serveur, sans TLS (profil test).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, MutableClockConfiguration.class})
class ErrorDispatchIT {

    @LocalServerPort
    private int port;

    private HttpResponse<String> send(String method, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                            .method(method, HttpRequest.BodyPublishers.noBody())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void expectSecurityHeaders(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("Content-Security-Policy"))
                .hasValueSatisfying(csp -> assertThat(csp).startsWith("default-src 'none'"));
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.headers().firstValue("Referrer-Policy")).contains("no-referrer");
        assertThat(response.headers().firstValue("Permissions-Policy")).isPresent();
        assertThat(response.headers().firstValue("Cross-Origin-Opener-Policy")).contains("same-origin");
        assertThat(response.headers().firstValue("Cross-Origin-Resource-Policy"))
                .contains("same-origin");
        assertThat(response.headers().firstValue("Cache-Control"))
                .hasValueSatisfying(cache -> assertThat(cache).contains("no-store"));
    }

    @Test
    @DisplayName(
            "ADR 0010 : une URL refusée par le pare-feu de Spring Security répond en Problem Details REQUEST_REJECTED")
    void adr0010FirewallRejectionIsAProblemDetail() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/a;b");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"REQUEST_REJECTED\"");
        assertThat(response.body()).contains("\"instance\":\"/api/v1/a;b\"");
    }

    @Test
    @DisplayName("TRN-20 : une requête refusée par le pare-feu reçoit les en-têtes de sécurité")
    void trn20FirewallRejectionCarriesTheSecurityHeaders() throws Exception {
        expectSecurityHeaders(send("GET", "/api/v1/a;b"));
    }

    @Test
    @DisplayName("TRN-20 : une requête TRACE refusée reçoit les en-têtes de sécurité")
    void trn20TraceRejectionCarriesTheSecurityHeaders() throws Exception {
        HttpResponse<String> response = send("TRACE", "/");

        assertThat(response.statusCode()).isEqualTo(405);
        expectSecurityHeaders(response);
    }
}
