package fr.vlegall.seal.common.web;

import fr.vlegall.seal.common.ErrorCode;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dispatch d'erreur du conteneur ({@code /error}) : requête refusée par le pare-feu de Spring Security, exception levée
 * dans un filtre, {@code sendError} de Tomcat. Remplace le contrôleur d'erreur de Spring Boot pour que ces réponses
 * soient, elles aussi, des Problem Details avec un {@code code} (CDC section 14, ADR 0010), sans message ni trace
 * (A02).
 */
@RestController
class ProblemDetailsErrorController implements ErrorController {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsErrorController.class);

    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        int status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code && code >= 400
                ? code
                : 500;
        // Hors de Spring MVC, un 400 vient du pare-feu ou du conteneur : une requête mal formée, pas un champ invalide.
        ErrorCode errorCode = status == 400 ? ErrorCode.REQUEST_REJECTED : ErrorCode.forStatus(status);
        if (status >= 500 && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable exception) {
            LOG.error("Erreur interne hors de Spring MVC", exception);
        }
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setProperty(ApiExceptionHandler.CODE, errorCode.name());
        return ResponseEntity.status(HttpStatusCode.valueOf(status)).body(problem);
    }
}
