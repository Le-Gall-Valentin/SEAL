package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/** AUTH-32 : l'inscription OAuth en attente, gardée dans la session. */
@DisplayName("AUTH-32 : attente gardée dans la session")
class PendingOAuthSessionTest {

    private static final Instant NOW = Instant.parse("2026-11-20T15:00:00Z");
    private static final ExternalIdentity IDENTITY =
            ExternalIdentity.of(OAuthProvider.GITHUB, "42", "octo@exemple.ca", false, "Octo");

    private final MutableClock clock = MutableClock.fixedAt(NOW);
    private final PendingOAuthSession pending = new PendingOAuthSession(clock);

    private static MockHttpServletRequest requestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    @Test
    @DisplayName("AUTH-32 : l'attente gardée se relit avec son identité et l'instant du retour")
    void auth32RememberedThenFound() {
        MockHttpServletRequest request = requestWith(new MockHttpSession());

        pending.remember(request, IDENTITY);

        assertThat(pending.find(request)).hasValueSatisfying(found -> {
            assertThat(found.identity()).isEqualTo(IDENTITY);
            assertThat(found.authenticatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("AUTH-32 : à 10 minutes, l'attente expire et quitte la session")
    void auth32ExpiresAndLeavesTheSession() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = requestWith(session);
        pending.remember(request, IDENTITY);
        clock.advance(Duration.ofMinutes(10));

        assertThat(pending.find(request)).isEmpty();
        assertThat(Collections.list(session.getAttributeNames())).isEmpty();
    }

    @Test
    @DisplayName("AUTH-32 : forget retire l'attente ; sans session ni attente, rien n'est créé ni écrit")
    void auth32ForgetAndNothingToRead() {
        MockHttpServletRequest request = requestWith(new MockHttpSession());
        pending.remember(request, IDENTITY);
        pending.forget(request);

        assertThat(pending.find(request)).isEmpty();
        MockHttpServletRequest withoutSession = new MockHttpServletRequest();
        assertThat(pending.find(withoutSession)).isEmpty();
        pending.forget(withoutSession);
        assertThat(withoutSession.getSession(false)).isNull();
    }

    @Test
    @DisplayName("ADR 0008 : la session ne garde qu'un instantané d'infrastructure, jamais un objet du domaine")
    void adr0008SessionHoldsAnInfrastructureSnapshot() {
        MockHttpSession session = new MockHttpSession();
        pending.remember(requestWith(session), IDENTITY);

        Object stored = session.getAttribute(PendingOAuthSession.ATTRIBUTE);
        assertThat(stored).isInstanceOf(java.io.Serializable.class);
        assertThat(stored.getClass().getPackageName()).doesNotContain("domain");
    }
}
