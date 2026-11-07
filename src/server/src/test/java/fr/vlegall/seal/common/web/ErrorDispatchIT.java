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

    @Test
    @DisplayName(
            "ADR 0010 : une URL refusée par le pare-feu de Spring Security répond en Problem Details REQUEST_REJECTED")
    void firewallRejectionIsAProblemDetail() throws Exception {
        HttpResponse<String> response;
        try (HttpClient client = HttpClient.newHttpClient()) {
            response = client.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/a;b"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"REQUEST_REJECTED\"");
    }
}
