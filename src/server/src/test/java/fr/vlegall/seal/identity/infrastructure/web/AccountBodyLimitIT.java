package fr.vlegall.seal.identity.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * SEC-01, ADR 0034 : un corps de plus de 16 Kio sous {@code /api/v1/account/} est refusé avant d'être lu, comme sous
 * {@code /api/v1/auth/} ; ces routes reçoivent aussi des mots de passe.
 */
@SealIntegrationTest
class AccountBodyLimitIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void reset() {
        mail.reset();
    }

    @Test
    @DisplayName(
            "SEC-01, ADR 0034 : plus de 16 Kio sur chaque route du compte répond 413 REQUEST_REJECTED, sans rien changer")
    void sec01AccountBodiesAreBounded() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 6);
        AuthClient client = new AuthClient(mvc, mail);
        Cookie full = client.verify(client.register("taille" + run + "@exemple.ca", "taille" + run));
        String big = "x".repeat(16_385);

        List<MockHttpServletRequestBuilder> requests = List.of(
                client.unsafe(patch("/api/v1/account/profile"), full, Map.of("displayName", big)),
                client.unsafe(
                        post("/api/v1/account/password"),
                        full,
                        Map.of("currentPassword", big, "newPassword", "raquette-sous-la-neige")),
                client.unsafe(post("/api/v1/account/email"), full, Map.of("email", big)),
                client.unsafe(post("/api/v1/account/email/confirm"), full, Map.of("code", big)));
        for (MockHttpServletRequestBuilder request : requests) {
            mvc.perform(request)
                    .andExpect(status().isContentTooLarge())
                    .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"))
                    .andExpect(header().exists("Content-Security-Policy"));
        }

        assertThat(jdbc.sql("SELECT display_name FROM users WHERE username = :username")
                        .param("username", "taille" + run)
                        .query(String.class)
                        .single())
                .isEqualTo("Valentin");
    }
}
