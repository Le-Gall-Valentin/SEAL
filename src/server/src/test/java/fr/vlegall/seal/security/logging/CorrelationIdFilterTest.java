package fr.vlegall.seal.security.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final String UUID_FORM = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String correlationIdSeenByTheChain() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));
        return seen.get();
    }

    @Test
    @DisplayName("SEC-21 : chaque requête porte un identifiant de corrélation, retiré à la fin de la requête")
    void sec21CorrelationIdLivesForTheRequestOnly() throws Exception {
        String first = correlationIdSeenByTheChain();

        assertThat(first).matches(UUID_FORM);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        assertThat(correlationIdSeenByTheChain()).matches(UUID_FORM).isNotEqualTo(first);
    }
}
