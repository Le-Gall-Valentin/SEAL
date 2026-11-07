package fr.vlegall.seal.common.web;

import fr.vlegall.seal.common.ErrorCode;
import fr.vlegall.seal.common.SealException;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convertit toute erreur de l'API en Problem Details (RFC 9457) portant un {@code code} stable (CDC section 14, ADR
 * 0010). Aucune réponse ne contient de trace de pile, de message d'exception ni de {@code detail} (A02, A10).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String CODE = "code";
    static final String FIELDS = "fields";

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SealException.class)
    ResponseEntity<ProblemDetail> handleSealException(SealException exception) {
        ResponseEntity<ProblemDetail> response = problem(
                exception.code(), HttpStatusCode.valueOf(exception.code().status()));
        return exception
                .retryAfter()
                .map(delay -> ResponseEntity.status(response.getStatusCode())
                        .header(HttpHeaders.RETRY_AFTER, Long.toString(secondsRoundedUp(delay)))
                        .body(response.getBody()))
                .orElse(response);
    }

    /** SEC-10 : {@code Retry-After} s'exprime en secondes entières ; on arrondit au-dessus pour ne jamais inviter trop tôt. */
    static long secondsRoundedUp(Duration delay) {
        return delay.toSeconds() + (delay.toNanosPart() > 0 ? 1 : 0);
    }

    /** QA-05 : sans session valide. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception) {
        return problem(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED);
    }

    /** QA-05 : jeton CSRF absent ou invalide, droits insuffisants. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        return problem(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    /** A10 : échec fermé ; la trace reste dans le journal, reliée par l'identifiant de corrélation (SEC-21). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        LOG.error("Erreur inattendue", exception);
        return problem(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Exceptions de Spring MVC : 400 devient {@code VALIDATION_FAILED}, 404 {@code NOT_FOUND}, le reste {@code REQUEST_REJECTED}. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
        problem.setDetail(null);
        problem.setProperty(CODE, codeFor(status).name());
        if (exception instanceof MethodArgumentNotValidException invalid) {
            problem.setProperty(FIELDS, fieldsInError(invalid));
        }
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        return ErrorCode.REQUEST_REJECTED;
    }

    private static List<String> fieldsInError(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .toList();
    }

    private static ResponseEntity<ProblemDetail> problem(ErrorCode code, HttpStatusCode status) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setProperty(CODE, code.name());
        return ResponseEntity.status(status).body(problem);
    }
}
