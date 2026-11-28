package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionFixationProtectionEvent;
import org.springframework.session.MapSession;

class SessionMigrationTest {

    private final List<Object> events = new ArrayList<>();
    private final ApplicationEventPublisher publisher = events::add;
    private final List<String> deleted = new ArrayList<>();
    private boolean stillStored = true;
    private final SessionMigration migration = new SessionMigration(publisher, id -> {
        deleted.add(id);
        return stillStored;
    });
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    private MockHttpSession existing() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("seal.test", "valeur");
        session.setMaxInactiveInterval(1800);
        // Session lue en base au début de la requête, pas créée par elle.
        session.setNew(false);
        request.setSession(session);
        // Le navigateur a présenté le cookie de cette session.
        request.setRequestedSessionId(session.getId());
        request.setRequestedSessionIdValid(true);
        return session;
    }

    private void authenticate() {
        migration.onAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("compte", null, List.of()),
                request,
                new MockHttpServletResponse());
    }

    @Test
    @DisplayName(
            "AUTH-23, ADR 0045 : l'authentification crée une nouvelle session qui reprend les attributs de l'ancienne")
    void auth23AuthenticationMigratesToANewSession() {
        MockHttpSession old = existing();

        authenticate();

        MockHttpSession renewed = (MockHttpSession) request.getSession(false);
        assertThat(renewed).isNotNull().isNotSameAs(old);
        assertThat(renewed.getId()).isNotEqualTo(old.getId());
        assertThat(old.isInvalid()).as("ancienne session invalidée").isTrue();
        assertThat(renewed.getAttribute("seal.test")).isEqualTo("valeur");
        assertThat(renewed.getMaxInactiveInterval()).isEqualTo(1800);
    }

    @Test
    @DisplayName("TRN-12, ADR 0044, ADR 0045 : la migration publie l'ancien et le nouvel identifiant")
    void trn12MigrationPublishesTheRename() {
        String oldId = existing().getId();

        authenticate();

        assertThat(events).singleElement().isInstanceOfSatisfying(SessionFixationProtectionEvent.class, event -> {
            assertThat(event.getOldSessionId()).isEqualTo(oldId);
            assertThat(event.getNewSessionId())
                    .isEqualTo(request.getSession(false).getId());
        });
    }

    @Test
    @DisplayName(
            "AUTH-52, ADR 0045 : la date de création de la première session est reportée, même après deux migrations")
    void auth52OriginSurvivesSuccessiveMigrations() {
        // Les sessions de test naissent dans la même milliseconde : l'origine posée une heure plus tôt les distingue.
        MockHttpSession first = existing();
        long created = first.getCreationTime() - Duration.ofHours(1).toMillis();
        first.setAttribute(SessionOrigin.ATTRIBUTE, created);

        authenticate();
        authenticate();

        MockHttpSession renewed = (MockHttpSession) request.getSession(false);
        assertThat(renewed.getAttribute(SessionOrigin.ATTRIBUTE)).isEqualTo(created);
        assertThat(SessionOrigin.createdAt(renewed)).isEqualTo(created);
    }

    @Test
    @DisplayName("AUTH-32, ADR 0045 : sans authentification, la session migre aussi, sans rien annoncer")
    void auth32MigrateWithoutAuthentication() {
        MockHttpSession old = existing();
        long created = old.getCreationTime();

        migration.migrate(request);

        MockHttpSession renewed = (MockHttpSession) request.getSession(false);
        assertThat(renewed).isNotNull().isNotSameAs(old);
        assertThat(old.isInvalid()).isTrue();
        assertThat(renewed.getAttribute("seal.test")).isEqualTo("valeur");
        assertThat(renewed.getMaxInactiveInterval()).isEqualTo(1800);
        assertThat(SessionOrigin.createdAt(renewed)).isEqualTo(created);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("AUTH-32, ADR 0045 : sans session, migrate en crée une, sans origine reportée")
    void auth32MigrateWithoutSessionCreatesOne() {
        migration.migrate(request);

        MockHttpSession created = (MockHttpSession) request.getSession(false);
        assertThat(created).isNotNull();
        assertThat(created.getAttribute(SessionOrigin.ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName(
            "AUTH-52, ADR 0045 : l'origine ne rajeunit jamais une session ; sans origine valide, la création compte")
    void auth52OriginNeverExtendsTheSession() {
        MockHttpSession http = new MockHttpSession();
        assertThat(SessionOrigin.createdAt(http)).isEqualTo(http.getCreationTime());
        http.setAttribute(SessionOrigin.ATTRIBUTE, http.getCreationTime() + 60_000);
        assertThat(SessionOrigin.createdAt(http)).as("origine postérieure").isEqualTo(http.getCreationTime());
        http.setAttribute(SessionOrigin.ATTRIBUTE, http.getCreationTime() - 60_000);
        assertThat(SessionOrigin.createdAt(http)).isEqualTo(http.getCreationTime() - 60_000);

        MapSession stored = new MapSession("s");
        Instant creation = Instant.ofEpochMilli(1_700_000_000_000L);
        stored.setCreationTime(creation);
        assertThat(SessionOrigin.createdAt(stored)).isEqualTo(creation);
        stored.setAttribute(
                SessionOrigin.ATTRIBUTE, creation.minus(Duration.ofHours(1)).toEpochMilli());
        assertThat(SessionOrigin.createdAt(stored)).isEqualTo(creation.minus(Duration.ofHours(1)));
        stored.setAttribute(
                SessionOrigin.ATTRIBUTE, creation.plus(Duration.ofHours(1)).toEpochMilli());
        assertThat(SessionOrigin.createdAt(stored)).isEqualTo(creation);
    }

    @Test
    @DisplayName("AUTH-23, ADR 0045 : la migration supprime elle-même l'ancienne ligne, sous l'ancien identifiant")
    void auth23MigrationDeletesTheStoredRow() {
        String oldId = existing().getId();

        authenticate();

        assertThat(deleted).containsExactly(oldId);
    }

    @Test
    @DisplayName(
            "AUTH-53, ADR 0045 : une session supprimée pendant la requête n'est pas migrée, donc pas ranimée (échec fermé)")
    void auth53SessionDeletedMeanwhileIsNotRevived() {
        MockHttpSession old = existing();
        stillStored = false;

        assertThatThrownBy(this::authenticate).isInstanceOf(SessionAuthenticationException.class);

        assertThat(old.isInvalid()).as("ancienne session invalidée").isTrue();
        assertThat(request.getSession(false)).as("aucune session créée").isNull();
        // TRN-12 : ses WebSockets se ferment, sans renommage annoncé.
        assertThat(events).containsExactly(new SessionsEndedEvent(Set.of(old.getId())));
    }

    @Test
    @DisplayName("AUTH-32, AUTH-53, ADR 0045 : migrate refuse aussi une session supprimée pendant la requête")
    void auth32MigrateRefusesASessionDeletedMeanwhile() {
        MockHttpSession old = existing();
        stillStored = false;

        assertThatThrownBy(() -> migration.migrate(request)).isInstanceOf(SessionAuthenticationException.class);

        assertThat(old.isInvalid()).isTrue();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName(
            "AUTH-23, ADR 0045 : une session créée par la requête même n'a pas de ligne à supprimer et migre sans contrôle")
    void auth23SessionCreatedByTheRequestIsNotChecked() {
        existing().setNew(true);
        stillStored = false;

        authenticate();

        assertThat(deleted).isEmpty();
        assertThat(request.getSession(false)).isNotNull();
    }

    @Test
    @DisplayName(
            "AUTH-23, scénario 16.1, ADR 0045 : la migration n'adopte jamais une autre session existante (second cookie)")
    void auth23MigrationNeverAdoptsAnotherExistingSession() {
        MockHttpSession old = existing();
        MockHttpSession planted = new MockHttpSession(null, "posee");
        planted.setNew(false);
        HttpServletRequest twoCookies = new HttpServletRequestWrapper(request) {
            @Override
            public HttpSession getSession(boolean create) {
                // Après l'invalidation, Spring Session retrouve la session d'un second cookie au lieu d'en créer une.
                return old.isInvalid() ? planted : old;
            }

            @Override
            public HttpSession getSession() {
                return getSession(true);
            }
        };

        assertThatThrownBy(() -> migration.onAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("compte", null, List.of()),
                        twoCookies,
                        new MockHttpServletResponse()))
                .isInstanceOf(SessionAuthenticationException.class);
        assertThat(planted.isInvalid()).as("session adoptée invalidée").isTrue();
        // TRN-12 : les WebSockets des deux sessions se ferment, sans renommage annoncé.
        assertThat(events).containsExactly(new SessionsEndedEvent(Set.of(old.getId(), "posee")));

        MockHttpSession plantedAgain = new MockHttpSession(null, "posee2");
        plantedAgain.setNew(false);
        MockHttpSession second = existing();
        HttpServletRequest twoCookiesAgain = new HttpServletRequestWrapper(request) {
            @Override
            public HttpSession getSession(boolean create) {
                return second.isInvalid() ? plantedAgain : second;
            }

            @Override
            public HttpSession getSession() {
                return getSession(true);
            }
        };
        events.clear();
        assertThatThrownBy(() -> migration.migrate(twoCookiesAgain)).isInstanceOf(SessionAuthenticationException.class);
        assertThat(plantedAgain.isInvalid()).isTrue();
        assertThat(events).containsExactly(new SessionsEndedEvent(Set.of(second.getId(), "posee2")));
    }

    @Test
    @DisplayName("AUTH-52, ADR 0045 : une origine présente mais illisible rend la session expirée (échec fermé)")
    void auth52UnreadableOriginExpiresTheSession() {
        MockHttpSession http = new MockHttpSession();
        http.setAttribute(SessionOrigin.ATTRIBUTE, "pas une date");
        assertThat(SessionOrigin.createdAt(http)).isZero();

        MapSession stored = new MapSession("s");
        stored.setAttribute(SessionOrigin.ATTRIBUTE, "pas une date");
        assertThat(SessionOrigin.createdAt(stored)).isEqualTo(Instant.EPOCH);
    }
}
