package fr.vlegall.seal.common.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import fr.vlegall.seal.security.ratelimit.RateLimit;
import fr.vlegall.seal.security.ratelimit.RateLimitExceededException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    @DisplayName("SEC-01 : une erreur de validation répond 400 VALIDATION_FAILED et liste les champs")
    void validationErrorListsTheFields() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields", contains("name")));
    }

    @Test
    @DisplayName("SEC-01 : un JSON mal formé est une erreur de validation")
    void malformedJsonIsAValidationError() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Une SealException garde son code et son statut")
    void sealExceptionKeepsItsCode() throws Exception {
        mvc.perform(get("/probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("ADR 0010 : une méthode non permise répond 405 REQUEST_REJECTED")
    void unsupportedMethodIsRejected() throws Exception {
        mvc.perform(delete("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
    }

    @Test
    @DisplayName("QA-05 : un échec d'authentification répond 401 UNAUTHENTICATED")
    void authenticationFailureIsUnauthenticated() throws Exception {
        mvc.perform(get("/probe/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("QA-05 : un accès refusé répond 403 FORBIDDEN")
    void accessDeniedIsForbidden() throws Exception {
        mvc.perform(get("/probe/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("A02 : une erreur inattendue répond 500 INTERNAL_ERROR sans le message de l'exception")
    void unexpectedErrorHidesTheExceptionMessage() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret interne"))));
    }

    @Test
    @DisplayName(
            "SEC-10 : un dépassement de limite répond 429 RATE_LIMITED avec Retry-After en secondes arrondies au-dessus")
    void rateLimitedResponseCarriesRetryAfter() throws Exception {
        mvc.perform(get("/probe/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "871"))
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    record ProbeRequest(@NotBlank String name) {}

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @PostMapping
        String create(@Valid @RequestBody ProbeRequest request) {
            return request.name();
        }

        @GetMapping
        String read() {
            return "ok";
        }

        @GetMapping("/missing")
        String missing() {
            throw new SealException(ErrorCode.NOT_FOUND);
        }

        @GetMapping("/unauthenticated")
        String unauthenticated() {
            throw new BadCredentialsException("identifiants refusés");
        }

        @GetMapping("/forbidden")
        String forbidden() {
            throw new AccessDeniedException("accès refusé");
        }

        @GetMapping("/rate-limited")
        String rateLimited() {
            throw new RateLimitExceededException(
                    RateLimit.REGISTRATIONS_PER_IP,
                    Duration.ofMinutes(14).plusSeconds(30).plusMillis(200));
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret interne");
        }
    }
}
