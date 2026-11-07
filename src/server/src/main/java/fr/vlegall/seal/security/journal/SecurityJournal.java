package fr.vlegall.seal.security.journal;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.logging.LogValues;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Journal d'imputabilité {@code security_events} (SEC-20). Chaque événement est écrit dans sa propre transaction :
 * une tentative refusée reste tracée même si l'opération qui l'a provoquée est annulée. Aucune valeur n'y entre sans
 * être assainie (SEC-21, A05).
 */
@Service
public class SecurityJournal {

    static final int USER_AGENT_MAX_LENGTH = 512;

    /** Instance partagée et immuable de Jackson 3 : les détails ne sont que des paires de chaînes. */
    private static final JsonMapper JSON = JsonMapper.shared();

    private final JdbcClient jdbc;

    public SecurityJournal(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SecurityEvent event) {
        HttpServletRequest request = currentRequest();
        jdbc.sql(
                        "INSERT INTO security_events (user_id, type, ip, user_agent, details) "
                                + "VALUES (CAST(:userId AS uuid), :type, CAST(:ip AS inet), :userAgent, CAST(:details AS jsonb))")
                .param("userId", userIdOf(event))
                .param("type", event.type().name())
                .param("ip", request == null ? null : request.getRemoteAddr())
                .param("userAgent", request == null ? null : userAgent(request))
                .param("details", JSON.writeValueAsString(sanitized(event.details())))
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
        if (userAgent == null) {
            return null;
        }
        String safe = LogValues.sanitize(userAgent);
        return safe.length() > USER_AGENT_MAX_LENGTH ? safe.substring(0, USER_AGENT_MAX_LENGTH) : safe;
    }

    private static Map<String, String> sanitized(Map<String, String> details) {
        Map<String, String> safe = new TreeMap<>();
        details.forEach((key, value) -> safe.put(LogValues.sanitize(key), LogValues.sanitize(value)));
        return safe;
    }
}
