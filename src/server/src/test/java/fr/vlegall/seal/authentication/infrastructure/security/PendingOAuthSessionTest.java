package fr.vlegall.seal.authentication.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.authentication.domain.model.ExternalIdentity;
import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.support.MutableClock;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
        assertThat(stored).isInstanceOf(Serializable.class);
        assertThat(stored.getClass().getPackageName()).doesNotContain("domain");
    }

    /** Instantanés qu'aucune identité valide ne décrit : relus d'une session ancienne ou altérée. */
    static List<Object> invalidAttributes() {
        return List.of(
                new PendingOAuthSession.Snapshot("OKTA", "42", "octo@exemple.ca", false, "Octo", NOW),
                new PendingOAuthSession.Snapshot("GITHUB", null, "octo@exemple.ca", false, "Octo", NOW),
                new PendingOAuthSession.Snapshot("GITHUB", "42", "octo@exemple.ca", false, "Octo", null),
                new PendingOAuthSession.Snapshot(
                        "GITHUB", "42", "octo@exemple.ca", false, "Octo" + (char) 0x202E + "x", NOW),
                "une autre valeur");
    }

    @ParameterizedTest
    @MethodSource("invalidAttributes")
    @DisplayName("AUTH-32, ADR 0008 : un instantané invalide ou d'un autre type n'est pas relu, et quitte la session")
    void auth32InvalidSnapshotIsDropped(Object stored) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingOAuthSession.ATTRIBUTE, stored);

        assertThat(pending.find(requestWith(session))).isEmpty();
        assertThat(session.getAttribute(PendingOAuthSession.ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("AUTH-32 : lire ou oublier une attente absente n'écrit rien dans la session (Spring Session JDBC)")
    void auth32ReadingNothingWritesNothing() {
        List<String> writes = new ArrayList<>();
        MockHttpSession session = new MockHttpSession() {
            @Override
            public void setAttribute(String name, Object value) {
                writes.add("set " + name);
                super.setAttribute(name, value);
            }

            @Override
            public void removeAttribute(String name) {
                writes.add("remove " + name);
                super.removeAttribute(name);
            }
        };
        MockHttpServletRequest request = requestWith(session);

        assertThat(pending.find(request)).isEmpty();
        pending.forget(request);

        assertThat(writes).isEmpty();
    }
}
