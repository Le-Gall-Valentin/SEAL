package fr.vlegall.seal.security.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import fr.vlegall.seal.support.SealIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** TRN-02 (ADR 0014, point 2) : origine unique, donc aucun CORS ; une autre origine ne reçoit jamais d'autorisation. */
@SealIntegrationTest
class CorsIT {

    private static final String OTHER_ORIGIN = "https://evil.example";

    @Autowired
    private MockMvc mvc;

    private static void expectNoCorsHeaders(ResultActions result) throws Exception {
        result.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Methods"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Headers"));
    }

    @Test
    @DisplayName("TRN-02 : une requête préliminaire venue d'une autre origine ne reçoit aucun en-tête CORS")
    void trn02PreflightFromAnotherOriginGetsNoCorsHeaders() throws Exception {
        expectNoCorsHeaders(mvc.perform(options("/api/v1/csrf")
                .secure(true)
                .header("Origin", OTHER_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-XSRF-TOKEN")));
    }

    @Test
    @DisplayName("TRN-02 : une requête simple venue d'une autre origine ne reçoit aucun en-tête CORS")
    void trn02SimpleRequestFromAnotherOriginGetsNoCorsHeaders() throws Exception {
        expectNoCorsHeaders(mvc.perform(get("/api/v1/csrf").secure(true).header("Origin", OTHER_ORIGIN)));
    }
}
