package fr.vlegall.seal.security.journal;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.logging.CorrelationIdFilter;
import fr.vlegall.seal.security.logging.LogValues;
import fr.vlegall.seal.security.web.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Journal d'imputabilité {@code security_events} (SEC-20), avec deux contrats d'écriture (ADR 0013 point 6, ADR 0021
 * point 7) :
 *
 * <ul>
 *   <li>{@link #recordStandalone} : un refus, ou un geste sans écriture métier autour (connexion, déconnexion,
 *       révocation de sessions), écrit dans sa propre transaction et appelé hors de toute transaction. Aucune
 *       annulation métier ne peut l'effacer, et aucune seconde connexion n'est retenue ;
 *   <li>{@link #recordInTransaction} : un succès, écrit dans la transaction métier qui doit exister ; annulé avec
 *       elle.
 * </ul>
 *
 * <p>Aucune valeur n'y entre sans être assainie et tronquée (SEC-21, A05). La date vient de l'horloge injectée, et
 * l'identifiant de corrélation de la requête est recopié dans les détails.
 */
@Service
public class SecurityJournal {

    static final int USER_AGENT_MAX_LENGTH = 512;
    static final int DETAIL_MAX_LENGTH = 256;
    static final String CORRELATION_ID = "correlationId";

    /** Instance partagée et immuable de Jackson 3 : les détails ne sont que des paires de chaînes. */
    private static final JsonMapper JSON = JsonMapper.shared();

    private final JdbcClient jdbc;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public SecurityJournal(JdbcClient jdbc, Clock clock, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.transactionManager = transactionManager;
    }

    /** Refus, ou geste sans écriture métier autour : propre transaction, hors de toute transaction appelante. */
    public void recordStandalone(SecurityEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Un événement autonome se journalise hors de toute transaction (ADR 0013, ADR 0021)");
        }
        transaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW).executeWithoutResult(status -> store(event));
    }

    /** Succès : dans la transaction métier, qui doit exister ; annulé avec elle. */
    public void recordInTransaction(SecurityEvent event) {
        transaction(TransactionDefinition.PROPAGATION_MANDATORY).executeWithoutResult(status -> store(event));
    }

    private TransactionTemplate transaction(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        return template;
    }

    private void store(SecurityEvent event) {
        HttpServletRequest request = currentRequest();
        jdbc.sql(
                        "INSERT INTO security_events (user_id, type, ip, user_agent, details, created_at) VALUES "
                                + "(CAST(:userId AS uuid), :type, CAST(:ip AS inet), :userAgent, CAST(:details AS jsonb), :createdAt)")
                .param("userId", userIdOf(event))
                .param("type", event.type().name())
                .param("ip", request == null ? null : ClientAddress.literal(request.getRemoteAddr()))
                .param("userAgent", request == null ? null : userAgent(request))
                .param("details", JSON.writeValueAsString(safeDetails(event.details())))
                .param("createdAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }

    private static @Nullable UUID userIdOf(SecurityEvent event) {
        UserId userId = event.userId();
        return userId == null ? null : userId.value();
    }

    private static @Nullable HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private static @Nullable String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null ? null : truncated(LogValues.sanitize(userAgent), USER_AGENT_MAX_LENGTH);
    }

    private static Map<String, String> safeDetails(Map<String, String> details) {
        Map<String, String> safe = new TreeMap<>();
        details.forEach((key, value) -> safe.put(
                truncated(LogValues.sanitize(key), DETAIL_MAX_LENGTH),
                truncated(LogValues.sanitize(value), DETAIL_MAX_LENGTH)));
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            safe.put(CORRELATION_ID, truncated(LogValues.sanitize(correlationId), DETAIL_MAX_LENGTH));
        }
        return safe;
    }

    private static String truncated(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
