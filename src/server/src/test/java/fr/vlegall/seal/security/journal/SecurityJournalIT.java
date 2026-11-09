package fr.vlegall.seal.security.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.logging.CorrelationIdFilter;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.support.SealIntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.IllegalTransactionStateException;
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

    @Autowired
    private MutableClock clock;

    private final String marker = UUID.randomUUID().toString();

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        MDC.remove(CorrelationIdFilter.MDC_KEY);
        clock.reset();
    }

    private SecurityEvent event(Map<String, String> details) {
        return new SecurityEvent(SecurityEventType.RATE_LIMIT_EXCEEDED, null, details);
    }

    private Map<String, Object> recordedRow() {
        return jdbc.sql("SELECT type, host(ip) AS ip, user_agent, user_id, created_at, "
                        + "details->>'limit' AS limit_name, details->>'correlationId' AS correlation_id "
                        + "FROM security_events WHERE details->>'marker' = ?")
                .param(marker)
                .query()
                .singleRow();
    }

    private long rowsWithMarker() {
        return jdbc.sql("SELECT count(*) FROM security_events WHERE details->>'marker' = ?")
                .param(marker)
                .query(Long.class)
                .single();
    }

    @Test
    @DisplayName("SEC-20 : un refus est enregistré avec l'IP et le navigateur, assainis")
    void sec20RefusalIsRecordedWithRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "x\r\n" + "a".repeat(600));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        journal.recordRefusal(event(Map.of("marker", marker, "limit", "A\nB")));

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
    @DisplayName("SEC-20 : hors requête, un refus est enregistré sans IP ni navigateur")
    void sec20RefusalOutsideARequestHasNoNetworkContext() {
        // Le framework de test attache une requête simulée à chaque test : on s'en détache, comme une tâche planifiée.
        RequestContextHolder.resetRequestAttributes();

        journal.recordRefusal(event(Map.of("marker", marker)));

        Map<String, Object> row = recordedRow();
        assertThat(row.get("ip")).isNull();
        assertThat(row.get("user_agent")).isNull();
    }

    @Test
    @DisplayName("ADR 0013 : un refus s'écrit hors de toute transaction, sans retenir une seconde connexion")
    void adr0013RefusalRefusesToRunInsideATransaction() {
        transactions.executeWithoutResult(status -> assertThatIllegalStateException()
                .isThrownBy(() -> journal.recordRefusal(event(Map.of("marker", marker)))));

        assertThat(rowsWithMarker()).isZero();
    }

    @Test
    @DisplayName("SEC-20 : un succès s'écrit dans la transaction métier")
    void sec20SuccessIsRecordedInTheBusinessTransaction() {
        transactions.executeWithoutResult(status -> journal.recordInTransaction(event(Map.of("marker", marker))));

        assertThat(rowsWithMarker()).isOne();
    }

    @Test
    @DisplayName("SEC-20 : un succès annulé avec sa transaction ne laisse aucune ligne")
    void sec20SuccessRollsBackWithItsTransaction() {
        transactions.executeWithoutResult(status -> {
            journal.recordInTransaction(event(Map.of("marker", marker)));
            status.setRollbackOnly();
        });

        assertThat(rowsWithMarker()).isZero();
    }

    @Test
    @DisplayName("ADR 0013 : un succès exige la transaction métier")
    void adr0013SuccessRequiresATransaction() {
        assertThatExceptionOfType(IllegalTransactionStateException.class)
                .isThrownBy(() -> journal.recordInTransaction(event(Map.of("marker", marker))));
    }

    @Test
    @DisplayName("SEC-20 : un événement rattaché à un utilisateur enregistre son identifiant")
    void sec20EventCarriesItsUser() {
        String username = "journal." + marker.substring(0, 8).replaceAll("[^a-z0-9]", "x");
        UUID userId = jdbc.sql(
                        "INSERT INTO users (username, display_name, email) VALUES (?, 'Journal', ?) RETURNING id")
                .params(username, username + "@exemple.ca")
                .query(UUID.class)
                .single();

        journal.recordRefusal(
                new SecurityEvent(SecurityEventType.RATE_LIMIT_EXCEEDED, new UserId(userId), Map.of("marker", marker)));

        assertThat(recordedRow().get("user_id")).isEqualTo(userId);
    }

    @Test
    @DisplayName("ADR 0013 : la date de l'événement vient de l'horloge injectée")
    void adr0013CreatedAtComesFromTheClock() {
        clock.advance(Duration.ofDays(3));

        journal.recordRefusal(event(Map.of("marker", marker)));

        OffsetDateTime createdAt = jdbc.sql("SELECT created_at FROM security_events WHERE details->>'marker' = ?")
                .param(marker)
                .query(OffsetDateTime.class)
                .single();
        assertThat(createdAt.toInstant())
                .isCloseTo(clock.instant(), org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("SEC-21 : l'identifiant de corrélation de la requête est recopié dans l'événement")
    void sec21CorrelationIdIsRecorded() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "correlation-test");

        journal.recordRefusal(event(Map.of("marker", marker)));

        assertThat(recordedRow().get("correlation_id")).isEqualTo("correlation-test");
    }

    @Test
    @DisplayName("ADR 0013 : chaque valeur des détails est tronquée à 256 caractères")
    void adr0013DetailValuesAreTruncated() {
        journal.recordRefusal(event(Map.of("marker", marker, "limit", "x".repeat(1000))));

        assertThat((String) recordedRow().get("limit_name")).hasSize(SecurityJournal.DETAIL_MAX_LENGTH);
    }

    @Test
    @DisplayName("SEC-20 : un événement de session s'écrit dans sa propre transaction, hors de toute autre")
    void sec20StandaloneEventIsRecordedInItsOwnTransaction() {
        journal.recordStandalone(event(Map.of("marker", marker)));

        assertThat(rowsWithMarker()).isEqualTo(1);
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> journal.recordStandalone(event(Map.of("marker", marker)))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rowsWithMarker()).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "SEC-20 : une adresse IPv6 avec zone est enregistrée sans sa zone, sans faire échouer le journal (A10)")
    void sec20ScopedIpv6AddressIsRecordedWithoutItsZone() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("fe80::1%eth0");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        journal.recordStandalone(event(Map.of("marker", marker)));

        assertThat(recordedRow().get("ip")).isEqualTo("fe80::1");
    }
}
