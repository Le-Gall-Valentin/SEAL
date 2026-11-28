package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class AbsoluteSessionTimeoutFilterTest {

    private final MockHttpSession session = new MockHttpSession();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockFilterChain chain = new MockFilterChain();
    private final List<Object> events = new ArrayList<>();

    private void runWithSessionAged(Duration age) throws Exception {
        request.setSession(session);
        MutableClock clock = MutableClock.fixedAt(Instant.ofEpochMilli(session.getCreationTime()));
        clock.advance(age);
        new AbsoluteSessionTimeoutFilter(clock, Duration.ofHours(12), events::add)
                .doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    @DisplayName("AUTH-52 : une session de moins de 12 heures est conservée")
    void filterKeepsAgeJustBelowTheLimit() throws Exception {
        runWithSessionAged(Duration.ofHours(12).minusMillis(1));

        assertThat(session.isInvalid()).isFalse();
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("AUTH-52 : une session de 12 heures est invalidée, la requête continue sans session")
    void filterInvalidatesAtTheLimit() throws Exception {
        runWithSessionAged(Duration.ofHours(12));

        assertThat(session.isInvalid()).isTrue();
        assertThat(chain.getRequest()).isSameAs(request);
        // TRN-12, ADR 0045 : les WebSockets de la session tombée se ferment aussitôt.
        assertThat(events).containsExactly(new SessionsEndedEvent(Set.of(session.getId())));
    }

    @Test
    @DisplayName("AUTH-52 : une requête sans session traverse le filtre sans en créer")
    void requestWithoutSessionIsUntouched() throws Exception {
        new AbsoluteSessionTimeoutFilter(MutableClock.fixedAt(Instant.EPOCH), Duration.ofHours(12), events::add)
                .doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getSession(false)).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("AUTH-52, ADR 0045 : les 12 heures se comptent depuis la première session, pas depuis la migration")
    void auth52AgeCountsFromTheOrigin() throws Exception {
        session.setAttribute(
                SessionOrigin.ATTRIBUTE,
                session.getCreationTime() - Duration.ofHours(11).toMillis());

        runWithSessionAged(Duration.ofHours(1));

        assertThat(session.isInvalid()).isTrue();
    }
}
