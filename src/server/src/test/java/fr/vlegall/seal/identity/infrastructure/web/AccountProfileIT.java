package fr.vlegall.seal.identity.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.support.AuthClient;
import fr.vlegall.seal.support.AuthClient.Registered;
import fr.vlegall.seal.support.RecordingMailSender;
import fr.vlegall.seal.support.SealIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** ACC-03 : {@code PATCH /api/v1/account/profile}, de bout en bout dans le contexte complet. */
@SealIntegrationTest
class AccountProfileIT {

    private static final String PROFILE = "/api/v1/account/profile";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private RecordingMailSender mail;

    private AuthClient client;

    /** Suffixe propre à ce test : la base est partagée par toute la série des tests d'intégration. */
    private final String run = UUID.randomUUID().toString().substring(0, 6);

    private String name(String base) {
        return base + run;
    }

    private String email(String base) {
        return base + run + "@exemple.ca";
    }

    @BeforeEach
    void setUp() {
        client = new AuthClient(mvc, mail);
    }

    @AfterEach
    void reset() {
        mail.reset();
    }

    private String storedDisplayName(String username) {
        return jdbc.sql("SELECT display_name FROM users WHERE username = :username")
                .param("username", username)
                .query(String.class)
                .single();
    }

    @Test
    @DisplayName("ACC-03 : le nom affiché est remplacé, normalisé NFC ; la réponse et /me rendent le compte courant")
    void acc03ChangesTheDisplayName() throws Exception {
        Cookie full = client.verify(client.register(email("profil"), name("profil")));
        String decomposed = "Léa Tremblay";

        mvc.perform(client.unsafe(patch(PROFILE), full, Map.of("displayName", decomposed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name("profil")))
                .andExpect(jsonPath("$.displayName").value("Léa Tremblay"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(storedDisplayName(name("profil"))).isEqualTo("Léa Tremblay");
        mvc.perform(client.from(get("/api/v1/auth/me")).cookie(full))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Léa Tremblay"));
    }

    @Test
    @DisplayName("ACC-03, SEC-01 : vide, trop long, caractère de direction ou de contrôle : 400 VALIDATION_FAILED")
    void acc03RejectsInvalidDisplayNames() throws Exception {
        Cookie full = client.verify(client.register(email("invalide"), name("invalide")));

        for (String value : new String[] {"", " ", "a".repeat(51), "Admin‮moc", "Nom\u0007", "x".repeat(201)}) {
            mvc.perform(client.unsafe(patch(PROFILE), full, Map.of("displayName", value)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields[0]").value("displayName"));
        }
        assertThat(storedDisplayName(name("invalide"))).isEqualTo("Valentin");
    }

    @Test
    @DisplayName("QA-05 : champ inconnu ou absent, 400 VALIDATION_FAILED ; rien n'est écrit")
    void qa05RejectsUnknownOrMissingFields() throws Exception {
        Cookie full = client.verify(client.register(email("champs"), name("champs")));

        mvc.perform(client.unsafe(patch(PROFILE), full, Map.of("displayName", "Nouveau", "username", "autre")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(client.unsafe(patch(PROFILE), full, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(storedDisplayName(name("champs"))).isEqualTo("Valentin");
    }

    @Test
    @DisplayName("QA-05 : 401 sans session, 403 sans jeton CSRF, 403 pour une session restreinte")
    void qa05AccessRules() throws Exception {
        Registered pending = client.register(email("restreint"), name("restreint"));

        mvc.perform(client.unsafe(patch(PROFILE), null, Map.of("displayName", "Anonyme")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(client.unsafe(patch(PROFILE), pending.session(), Map.of("displayName", "Restreint")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Cookie full = client.verify(pending);
        mvc.perform(client.from(patch(PROFILE))
                        .cookie(full)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Sans jeton\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(storedDisplayName(name("restreint"))).isEqualTo("Valentin");
    }
}
