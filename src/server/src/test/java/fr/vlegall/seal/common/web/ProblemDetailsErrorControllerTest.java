package fr.vlegall.seal.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ProblemDetailsErrorControllerTest {

    private final ProblemDetailsErrorController controller = new ProblemDetailsErrorController();

    private ResponseEntity<ProblemDetail> errorFor(Integer status, Throwable exception) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        if (status != null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        if (exception != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        }
        return controller.error(request);
    }

    private static Object codeOf(ResponseEntity<ProblemDetail> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getProperties().get("code");
    }

    @Test
    @DisplayName("ADR 0010 : une requête refusée avant Spring MVC (pare-feu, Tomcat) répond 400 REQUEST_REJECTED")
    void malformedRequestIsRejected() {
        ResponseEntity<ProblemDetail> response = errorFor(400, null);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(response)).isEqualTo("REQUEST_REJECTED");
    }

    @Test
    @DisplayName("ADR 0010 : une ressource introuvable hors de Spring MVC répond 404 NOT_FOUND")
    void notFoundKeepsItsCode() {
        ResponseEntity<ProblemDetail> response = errorFor(404, null);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(codeOf(response)).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("A02, A10 : une exception levée dans un filtre répond 500 INTERNAL_ERROR sans son message")
    void filterExceptionIsAnInternalErrorWithoutItsMessage() {
        ResponseEntity<ProblemDetail> response = errorFor(500, new IllegalStateException("secret interne"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(codeOf(response)).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getDetail()).isNull();
        assertThat(response.getBody().toString()).doesNotContain("secret interne");
    }

    @Test
    @DisplayName("A10 : sans statut connu, l'erreur est traitée comme interne")
    void missingStatusIsAnInternalError() {
        ResponseEntity<ProblemDetail> response = errorFor(null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(codeOf(response)).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("ADR 0010 : instance porte le chemin demandé, pas « /error »")
    void instanceIsTheRequestedPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/a;b");

        ResponseEntity<ProblemDetail> response = controller.error(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInstance()).hasToString("/api/v1/a;b");
    }
}
