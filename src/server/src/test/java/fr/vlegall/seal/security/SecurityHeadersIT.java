package fr.vlegall.seal.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SecurityHeadersIT {

    /** Valeur exacte de la section 12.3 du CDC, recopiée plutôt que recalculée. */
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data: blob:; font-src 'self'; connect-src 'self' wss://localhost:8443; "
            + "manifest-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'";

    @Autowired
    private MockMvc mvc;

    private static void expectSecurityHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string("Content-Security-Policy", CSP))
                .andExpect(header().string("Strict-Transport-Security", "max-age=300"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()"))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("TRN-20 : la page du client porte tous les en-têtes de la section 12.3")
    void trn20ClientPageCarriesTheSecurityHeaders() throws Exception {
        expectSecurityHeaders(mvc.perform(get("/").secure(true)));
    }

    @Test
    @DisplayName("TRN-20 : une réponse refusée porte aussi les en-têtes")
    void trn20RefusedResponseCarriesTheSecurityHeaders() throws Exception {
        expectSecurityHeaders(mvc.perform(get("/api/v1/unknown").secure(true)).andExpect(status().isForbidden()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/unknown", "/actuator/env", "/ws"})
    @DisplayName("A01 : toute route non ouverte explicitement est refusée")
    void everyRouteNotExplicitlyOpenedIsDenied(String path) throws Exception {
        mvc.perform(get(path).secure(true)).andExpect(status().isForbidden());
        mvc.perform(post(path).secure(true)).andExpect(status().isForbidden());
    }
}
