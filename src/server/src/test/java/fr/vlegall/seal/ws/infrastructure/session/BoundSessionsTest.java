package fr.vlegall.seal.ws.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.session.SealPrincipal;
import fr.vlegall.seal.security.session.SessionOrigin;
import fr.vlegall.seal.support.MutableClock;
import fr.vlegall.seal.ws.domain.model.FakeConnection;
import fr.vlegall.seal.ws.domain.model.SessionBinding;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;

class BoundSessionsTest {

    private static final UserId USER = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000001"));
    private static final UserId OTHER = new UserId(UUID.fromString("0192f7a1-0000-7000-8000-000000000002"));

    private final MutableClock clock = MutableClock.followingSystem();
    private final MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
    private OptionalLong passwordVersion = OptionalLong.of(1);
    private final BoundSessions sessions =
            new BoundSessions(repository, clock, Duration.ofHours(12), user -> passwordVersion);

    /** Session complète du compte {@code owner}, telle que SessionEstablisher l'écrit. */
    private MapSession stored(String id, String authority, UserId owner) {
        MapSession session = new MapSession(id);
        session.setMaxInactiveInterval(Duration.ofMinutes(30));
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                        new SealPrincipal(owner, 1L), null, List.of(new SimpleGrantedAuthority(authority)))));
        repository.save(session);
        return session;
    }

    private MapSession full(String id, UserId owner) {
        return stored(id, SealAuthorities.ACCOUNT_ACTIVE, owner);
    }

    private FakeConnection connection(String sessionId) {
        return new FakeConnection(USER, sessionId, clock.instant());
    }

    private Instant lastAccess(String id) {
        return repository.findById(id).getLastAccessedTime();
    }

    @Test
    @DisplayName("TRN-12 : une session complète du même compte, en cours, garde sa connexion")
    void trn12LiveFullSessionOfTheSameAccount() {
        full("s", USER);

        assertThat(sessions.alive(connection("s"))).isTrue();
    }

    @Test
    @DisplayName("TRN-12 : une session disparue, d'un autre compte ou restreinte ne garde pas la connexion")
    void trn12GoneForeignOrRestrictedSessionEndsTheConnection() {
        full("autre", OTHER);
        stored("restreinte", SealAuthorities.ACCOUNT_PENDING_EMAIL, USER);
        repository.save(new MapSession("vide"));

        assertThat(sessions.alive(connection("disparue"))).isFalse();
        assertThat(sessions.alive(connection("autre"))).isFalse();
        assertThat(sessions.alive(connection("restreinte"))).isFalse();
        assertThat(sessions.alive(connection("vide"))).isFalse();
    }

    @Test
    @DisplayName(
            "TRN-12, ADR 0023, ADR 0044 : une session dont le mot de passe a changé depuis ne garde pas sa connexion")
    void adr0023StalePasswordVersionEndsTheConnection() {
        full("s", USER);
        assertThat(sessions.alive(connection("s"))).isTrue();

        passwordVersion = OptionalLong.of(2);

        assertThat(sessions.alive(connection("s"))).isFalse();
        assertThat(sessions.userActed(connection("s"))).isFalse();
    }

    @Test
    @DisplayName(
            "ACC-01, TRN-12, ADR 0044 : pendant un renommage pas encore écrit, la version n'est jugée que sous l'identifiant courant")
    void acc01VersionIsJudgedUnderTheCurrentIdOnly() {
        full("ancien", USER);
        FakeConnection connection = connection("ancien");
        connection.follow("nouveau", clock.instant());
        // Le mot de passe vient de changer en base ; la session regravée n'est pas encore écrite sous son nouvel id.
        passwordVersion = OptionalLong.of(2);

        assertThat(sessions.alive(connection)).isTrue();

        full("nouveau", USER);
        assertThat(sessions.alive(connection))
                .as("sous l'id courant, l'ancienne version ne vaut plus")
                .isFalse();
    }

    @Test
    @DisplayName(
            "TRN-12, ADR 0023, ADR 0044 : passé 30 s, un renommage jamais écrit ne dispense plus du contrôle de version")
    void trn12UnwrittenRenameIsToleratedThirtySecondsOnly() {
        full("ancien", USER);
        FakeConnection connection = connection("ancien");
        connection.follow("nouveau", clock.instant());
        passwordVersion = OptionalLong.of(2);
        assertThat(sessions.alive(connection)).isTrue();

        clock.advance(Duration.ofSeconds(30));

        assertThat(sessions.alive(connection)).as("échec fermé").isFalse();
    }

    @Test
    @DisplayName(
            "TRN-12, ADR 0045 : pendant 30 s après un renommage, une session migrée pas encore écrite garde sa connexion")
    void trn12MigrationGapIsToleratedThirtySecondsOnly() {
        // La migration a supprimé l'ancienne ligne ; la nouvelle n'est écrite qu'à la fin de la requête.
        FakeConnection connection = connection("ancien");
        connection.follow("nouveau", clock.instant());

        assertThat(sessions.alive(connection)).isTrue();
        assertThat(sessions.userActed(connection)).isTrue();

        clock.advance(Duration.ofSeconds(30));

        assertThat(sessions.alive(connection)).as("échec fermé").isFalse();
        assertThat(sessions.userActed(connection)).isFalse();
    }

    @Test
    @DisplayName("AUTH-52, ADR 0045 : les 12 heures se comptent depuis la première session, reportée par la migration")
    void auth52AgeCountsFromTheOrigin() {
        MapSession migrated = full("migree", USER);
        migrated.setAttribute(
                SessionOrigin.ATTRIBUTE,
                clock.instant().minus(Duration.ofHours(12)).toEpochMilli());
        repository.save(migrated);

        assertThat(sessions.alive(connection("migree"))).isFalse();
    }

    @Test
    @DisplayName("AUTH-52 : 30 minutes d'inactivité, ou 12 heures d'âge, à l'horloge de SEAL, finissent la session")
    void auth52IdleOrAbsoluteExpiryByTheClock() {
        full("inactive", USER);
        MapSession old = full("ancienne", USER);
        old.setCreationTime(clock.instant().minus(Duration.ofHours(12)));
        repository.save(old);

        assertThat(sessions.alive(connection("ancienne"))).isFalse();
        clock.advance(Duration.ofMinutes(30));
        assertThat(sessions.alive(connection("inactive"))).isFalse();
    }

    @Test
    @DisplayName("TRN-12, AUTH-23 : tant que le nouvel identifiant n'est pas enregistré, la connexion suit l'ancien")
    void trn12RenamePendingKeepsTheConnection() {
        full("ancien", USER);
        FakeConnection connection = connection("ancien");
        connection.follow("nouveau", clock.instant());
        Instant before = lastAccess("ancien");
        clock.advance(Duration.ofMinutes(2));

        assertThat(sessions.alive(connection)).isTrue();
        assertThat(sessions.userActed(connection)).isTrue();
        assertThat(lastAccess("ancien"))
                .as("aucune écriture sous l'ancien identifiant")
                .isEqualTo(before);
    }

    @Test
    @DisplayName(
            "AUTH-55, ADR 0044 : après deux renommages non confirmés, la session vit sous l'identifiant encore en base")
    void auth55ChainedRenamesFallBackToTheStoredId() {
        full("a0", USER);
        FakeConnection connection = connection("a0");
        connection.follow("a1", clock.instant());
        connection.follow("a2", clock.instant());

        assertThat(sessions.alive(connection)).isTrue();
        assertThat(connection.binding().formers()).containsExactly("a1", "a0");
    }

    @Test
    @DisplayName("TRN-12, ADR 0044 : une fois le nouvel identifiant en base, les anciens sont oubliés")
    void trn12ConfirmedRenameSettles() {
        FakeConnection connection = connection("a0");
        connection.follow("a1", clock.instant());
        full("a1", USER);

        assertThat(sessions.alive(connection)).isTrue();

        assertThat(connection.binding()).isEqualTo(SessionBinding.of("a1"));
    }

    @Test
    @DisplayName("AUTH-52, ADR 0043 : une action prolonge la session si son dernier accès date d'une minute au moins")
    void auth52UserActionExtendsAtMostOncePerMinute() {
        full("s", USER);
        Instant before = lastAccess("s");

        clock.advance(Duration.ofSeconds(59));
        assertThat(sessions.userActed(connection("s"))).isTrue();
        assertThat(lastAccess("s")).isEqualTo(before);

        clock.advance(Duration.ofSeconds(2));
        assertThat(sessions.userActed(connection("s"))).isTrue();
        assertThat(lastAccess("s")).isAfter(before.plus(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("AUTH-52, A10 : une action sur une session finie n'est pas prise et ne la ranime pas")
    void auth52UserActionOnAnEndedSessionIsRefused() {
        full("s", USER);
        clock.advance(Duration.ofMinutes(31));

        assertThat(sessions.userActed(connection("s"))).isFalse();
        assertThat(sessions.userActed(connection("disparue"))).isFalse();
    }
}
