package fr.vlegall.seal.security.journal;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.SealIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SealIntegrationTest
class SecurityJournalIT {

    @Autowired
    private SecurityJournal journal;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transactions;

    private final String marker = UUID.randomUUID().toString();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private SecurityEvent event(Map<String, String> details) {
        return new SecurityEvent(SecurityEventType.RATE_LIMIT_EXCEEDED, null, details);
    }

    private Map<String, Object> recordedRow() {
        return jdbc.sql("SELECT type, host(ip) AS ip, user_agent, user_id, details->>'limit' AS limit_name "
                        + "FROM security_events WHERE details->>'marker' = ?")
                .param(marker)
                .query()
                .singleRow();
    }

    @Test
    @DisplayName("SEC-20 : l'événement est enregistré avec l'IP et le navigateur, assainis")
    void sec20EventIsRecordedWithRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "x\r\n" + "a".repeat(600));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        journal.record(event(Map.of("marker", marker, "limit", "A\nB")));

        Map<String, Object> row = recordedRow();
        assertThat(row.get("type")).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(row.get("ip")).isEqualTo("203.0.113.7");
        assertThat((String) row.get("user_agent"))
                .hasSize(SecurityJournal.USER_AGENT_MAX_LENGTH)
                .startsWith("x__a")
                .doesNotContain("\r", "\n");
        assertThat(row.get("limit_name")).isEqualTo("A_B");
        assertThat(row.get("user_id")).isNull();
    }

    @Test
    @DisplayName("SEC-20 : hors requête, l'événement est enregistré sans IP ni navigateur")
    void sec20EventOutsideARequestHasNoNetworkContext() {
        // Le framework de test attache une requête simulée à chaque test : on s'en détache, comme une tâche planifiée.
        RequestContextHolder.resetRequestAttributes();

        journal.record(event(Map.of("marker", marker)));

        Map<String, Object> row = recordedRow();
        assertThat(row.get("ip")).isNull();
        assertThat(row.get("user_agent")).isNull();
    }

    @Test
    @DisplayName("SEC-20 : l'événement reste enregistré si la transaction appelante est annulée")
    void sec20EventSurvivesARolledBackTransaction() {
        transactions.executeWithoutResult(status -> {
            journal.record(event(Map.of("marker", marker)));
            status.setRollbackOnly();
        });

        assertThat(recordedRow()).containsEntry("type", "RATE_LIMIT_EXCEEDED");
    }
}
