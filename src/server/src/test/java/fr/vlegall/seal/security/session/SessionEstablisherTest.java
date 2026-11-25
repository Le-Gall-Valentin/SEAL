package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.SerializationUtils;

class SessionEstablisherTest {

    private static final UserId USER = new UserId(UUID.fromString("0190f1d2-7e3a-7b4c-8d5e-6f7a8b9c0d1e"));
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00Z");

    /** Version courante du mot de passe de {@link #USER}, gravée dans ses sessions (ADR 0023). */
    private static final long VERSION = 4;

    private final MutableClock clock = MutableClock.fixedAt(NOW);
    private OptionalLong storedVersion = OptionalLong.of(VERSION);
    private final List<Object> published = new ArrayList<>();
    private final SessionEstablisher sessions = new SessionEstablisher(
            CookieCsrfTokenRepository.withHttpOnlyFalse(), clock, user -> storedVersion, List.of(), published::add);

    /** Authentification d'une connexion réussie d'un compte actif, telle que la rend le fournisseur. */
    private static Authentication activeLogin() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new SealPrincipal(USER, VERSION),
                null,
                List.of(new SimpleGrantedAuthority(SealAuthorities.ACCOUNT_ACTIVE)));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest requestWithSession(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setSession(session);
        // Une vraie requête nomme sa session par le cookie ; sans cela, MockHttpServletRequest la dit invalide.
        request.setRequestedSessionId(session.getId());
        request.setCookies(new Cookie("XSRF-TOKEN", "ancien-jeton"));
        return request;
    }

    private static Authentication storedAuthentication(MockHttpServletRequest request) {
        SecurityContext context = (SecurityContext) request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        return context.getAuthentication();
    }

    private static Object[] authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toArray();
    }

    @Test
    @DisplayName("AUTH-16 : la session restreinte porte le compte en attente et le facteur mot de passe")
    void auth16RestrictedSessionCarriesPendingStatusAndPasswordFactor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);

        sessions.openRestricted(USER, request, new MockHttpServletResponse());

        Authentication authentication = storedAuthentication(request);
        assertThat(authentication.getPrincipal()).isEqualTo(new SealPrincipal(USER, VERSION));
        assertThat(authentication.getName()).isEqualTo(USER.value().toString());
        assertThat(authorities(authentication)).containsExactly("ACCOUNT_PENDING_EMAIL", "FACTOR_PASSWORD");
        assertThat(authentication.getAuthorities())
                .filteredOn(FactorGrantedAuthority.class::isInstance)
                .singleElement()
                .extracting(a -> ((FactorGrantedAuthority) a).getIssuedAt())
                .isEqualTo(NOW);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authentication);
    }

    @Test
    @DisplayName("AUTH-23 : une session déjà présente change d'identifiant à l'ouverture (fixation)")
    void auth23ExistingSessionChangesItsIdentifier() {
        MockHttpSession session = new MockHttpSession(null, "session-fixee");
        MockHttpServletRequest request = requestWithSession(session);

        sessions.openRestricted(USER, request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getId()).isNotEqualTo("session-fixee");
    }

    @Test
    @DisplayName("TRN-12, ADR 0043 : un renouvellement annonce l'ancien et le nouvel identifiant, et le compte")
    void trn12RenewalIsAnnounced() {
        MockHttpSession session = new MockHttpSession(null, "avant");
        MockHttpServletRequest request = requestWithSession(session);

        sessions.open(activeLogin(), request, new MockHttpServletResponse());

        String current = request.getSession(false).getId();
        assertThat(published).containsExactly(new SessionRenewedEvent("avant", current, USER));
    }

    @Test
    @DisplayName("TRN-12, ADR 0043 : une première session n'annonce aucun renouvellement")
    void trn12FirstSessionIsNotARenewal() {
        sessions.open(activeLogin(), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("AUTH-32, TRN-12 : la session rendue anonyme pour une inscription OAuth est annoncée finie")
    void auth32AnonymousRenewalEndsTheSession() {
        MockHttpSession session = new MockHttpSession(null, "complete");
        MockHttpServletRequest request = requestWithSession(session);

        sessions.renew(request, new MockHttpServletResponse());

        assertThat(published).containsExactly(new SessionsEndedEvent(Set.of("complete")));
    }

    @Test
    @DisplayName("AUTH-53, TRN-12 : la déconnexion annonce la session finie, pour fermer ses WebSockets")
    void auth53LogoutAnnouncesTheEndedSession() {
        MockHttpSession session = new MockHttpSession(null, "finie");
        MockHttpServletRequest request = requestWithSession(session);

        sessions.close(request, new MockHttpServletResponse());

        assertThat(published).containsExactly(new SessionsEndedEvent(Set.of("finie")));
    }

    @Test
    @DisplayName("AUTH-60 : l'ouverture d'une session retire l'ancien jeton CSRF")
    void auth60OpeningASessionDropsTheOldCsrfToken() {
        MockHttpServletRequest request = requestWithSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();

        sessions.openRestricted(USER, request, response);

        Cookie cleared = response.getCookie("XSRF-TOKEN");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("AUTH-16 : la vérification rend la session complète, avec un nouvel identifiant et le même facteur")
    void auth16UpgradeMakesTheSessionFullWithANewIdentifier() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        sessions.openRestricted(USER, request, new MockHttpServletResponse());
        String restrictedId = request.getSession(false).getId();
        request.setRequestedSessionId(restrictedId);
        clock.advance(java.time.Duration.ofMinutes(3));

        sessions.upgradeToFull(USER, request, new MockHttpServletResponse());

        Authentication authentication = storedAuthentication(request);
        assertThat(request.getSession(false).getId()).isNotEqualTo(restrictedId);
        assertThat(authorities(authentication)).containsExactly("ACCOUNT_ACTIVE", "FACTOR_PASSWORD");
        assertThat(authentication.getAuthorities())
                .filteredOn(FactorGrantedAuthority.class::isInstance)
                .singleElement()
                .extracting(a -> ((FactorGrantedAuthority) a).getIssuedAt())
                .as("le facteur garde la date où il a été obtenu (AUTH-55)")
                .isEqualTo(NOW);
        assertThat(sessions.factors(authentication)).containsExactly("PASSWORD");
    }

    @Test
    @DisplayName(
            "AUTH-55 : la ré-authentification redate le facteur, sous un nouvel identifiant et sans l'ancien jeton CSRF")
    void auth55ReauthenticationRedatesTheFactor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        sessions.open(activeLogin(), request, new MockHttpServletResponse());
        String openedId = request.getSession(false).getId();
        request.setRequestedSessionId(openedId);
        request.setCookies(new Cookie("XSRF-TOKEN", "ancien-jeton"));
        clock.advance(java.time.Duration.ofHours(2));
        MockHttpServletResponse response = new MockHttpServletResponse();

        sessions.refreshFactor(SealAuthorities.FACTOR_PASSWORD, request, response);

        Authentication authentication = storedAuthentication(request);
        assertThat(request.getSession(false).getId()).isNotEqualTo(openedId);
        assertThat(authentication.getPrincipal()).isEqualTo(new SealPrincipal(USER, VERSION));
        assertThat(authorities(authentication)).containsExactly("ACCOUNT_ACTIVE", "FACTOR_PASSWORD");
        assertThat(authentication.getAuthorities())
                .filteredOn(FactorGrantedAuthority.class::isInstance)
                .singleElement()
                .extracting(a -> ((FactorGrantedAuthority) a).getIssuedAt())
                .isEqualTo(NOW.plus(java.time.Duration.ofHours(2)));
        assertThat(response.getCookie("XSRF-TOKEN"))
                .isNotNull()
                .extracting(Cookie::getMaxAge)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("AUTH-55 : la ré-authentification OAuth redate son facteur et garde les autres tels quels")
    void auth55ReauthenticationKeepsTheOtherFactors() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        sessions.openWithAuthorizationCode(USER, true, request, new MockHttpServletResponse());
        request.setRequestedSessionId(request.getSession(false).getId());
        clock.advance(java.time.Duration.ofMinutes(30));

        sessions.refreshFactor(SealAuthorities.FACTOR_AUTHORIZATION_CODE, request, new MockHttpServletResponse());

        Authentication authentication = storedAuthentication(request);
        assertThat(authentication.getPrincipal()).isEqualTo(new SealPrincipal(USER));
        assertThat(authorities(authentication)).containsExactly("ACCOUNT_ACTIVE", "FACTOR_AUTHORIZATION_CODE");
        assertThat(authentication.getAuthorities())
                .filteredOn(FactorGrantedAuthority.class::isInstance)
                .singleElement()
                .extracting(a -> ((FactorGrantedAuthority) a).getIssuedAt())
                .isEqualTo(NOW.plus(java.time.Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName(
            "ACC-01, ADR 0023 : la session qui change le mot de passe porte la nouvelle version et un facteur redaté")
    void acc01PasswordChangeRegravesTheSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        sessions.open(activeLogin(), request, new MockHttpServletResponse());
        String openedId = request.getSession(false).getId();
        request.setRequestedSessionId(openedId);
        clock.advance(java.time.Duration.ofHours(1));

        sessions.passwordChanged(VERSION + 1, request, new MockHttpServletResponse());

        Authentication authentication = storedAuthentication(request);
        assertThat(request.getSession(false).getId()).isNotEqualTo(openedId);
        assertThat(authentication.getPrincipal()).isEqualTo(new SealPrincipal(USER, VERSION + 1));
        assertThat(authorities(authentication)).containsExactly("ACCOUNT_ACTIVE", "FACTOR_PASSWORD");
        assertThat(authentication.getAuthorities())
                .filteredOn(FactorGrantedAuthority.class::isInstance)
                .singleElement()
                .extracting(a -> ((FactorGrantedAuthority) a).getIssuedAt())
                .isEqualTo(NOW.plus(java.time.Duration.ofHours(1)));
    }

    @Test
    @DisplayName("AUTH-55 : sans session complète, rien n'est redaté (échec fermé)")
    void auth55ReauthenticationNeedsAFullSession() {
        MockHttpServletRequest anonymous = new MockHttpServletRequest();
        anonymous.setSecure(true);
        assertThatIllegalStateException()
                .isThrownBy(() -> sessions.refreshFactor(
                        SealAuthorities.FACTOR_PASSWORD, anonymous, new MockHttpServletResponse()));

        MockHttpServletRequest restricted = new MockHttpServletRequest();
        restricted.setSecure(true);
        sessions.openRestricted(USER, restricted, new MockHttpServletResponse());
        assertThatIllegalStateException()
                .isThrownBy(() -> sessions.refreshFactor(
                        SealAuthorities.FACTOR_PASSWORD, restricted, new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("AUTH-20 : la connexion d'un compte actif ouvre une session complète, sous un nouvel identifiant")
    void auth20FullSessionCarriesActiveStatusAndPasswordFactor() {
        MockHttpSession anonymous = new MockHttpSession(null, "session-anonyme");
        MockHttpServletRequest request = requestWithSession(anonymous);
        MockHttpServletResponse response = new MockHttpServletResponse();

        sessions.open(activeLogin(), request, response);

        Authentication authentication = storedAuthentication(request);
        assertThat(request.getSession(false).getId()).isNotEqualTo("session-anonyme");
        assertThat(authentication.getPrincipal()).isEqualTo(new SealPrincipal(USER, VERSION));
        assertThat(authorities(authentication)).containsExactly("ACCOUNT_ACTIVE", "FACTOR_PASSWORD");
        assertThat(authentication.getAuthorities())
                .filteredOn(FactorGrantedAuthority.class::isInstance)
                .singleElement()
                .extracting(a -> ((FactorGrantedAuthority) a).getIssuedAt())
                .isEqualTo(NOW);
        assertThat(response.getCookie("XSRF-TOKEN"))
                .isNotNull()
                .extracting(Cookie::getMaxAge)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("AUTH-20 : la session ouverte suit l'authentification rendue : complète si vérifiée, sinon restreinte")
    void auth20OpenFollowsTheAuthenticatedStatus() {
        MockHttpServletRequest active = new MockHttpServletRequest();
        sessions.open(
                UsernamePasswordAuthenticationToken.authenticated(
                        new SealPrincipal(USER),
                        null,
                        List.of(new SimpleGrantedAuthority(SealAuthorities.ACCOUNT_ACTIVE))),
                active,
                new MockHttpServletResponse());
        MockHttpServletRequest pending = new MockHttpServletRequest();
        sessions.open(
                UsernamePasswordAuthenticationToken.authenticated(
                        new SealPrincipal(USER),
                        null,
                        List.of(new SimpleGrantedAuthority(SealAuthorities.ACCOUNT_PENDING_EMAIL))),
                pending,
                new MockHttpServletResponse());

        assertThat(authorities(storedAuthentication(active))).containsExactly("ACCOUNT_ACTIVE", "FACTOR_PASSWORD");
        assertThat(authorities(storedAuthentication(pending)))
                .containsExactly("ACCOUNT_PENDING_EMAIL", "FACTOR_PASSWORD");
    }

    @Test
    @DisplayName(
            "AUTH-70 : la session d'une connexion garde la version du mot de passe, complète ou restreinte (ADR 0023)")
    void auth70OpenKeepsTheCredentialVersion() {
        for (String status : List.of(SealAuthorities.ACCOUNT_ACTIVE, SealAuthorities.ACCOUNT_PENDING_EMAIL)) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            sessions.open(
                    UsernamePasswordAuthenticationToken.authenticated(
                            new SealPrincipal(USER, 7L), null, List.of(new SimpleGrantedAuthority(status))),
                    request,
                    new MockHttpServletResponse());

            assertThat(storedAuthentication(request).getPrincipal()).isEqualTo(new SealPrincipal(USER, 7L));
        }
    }

    @Test
    @DisplayName("AUTH-70 : la session d'inscription grave la version courante du mot de passe (ADR 0023)")
    void auth70RestrictedSessionCarriesTheCurrentVersion() {
        MockHttpServletRequest withPassword = new MockHttpServletRequest();
        sessions.openRestricted(USER, withPassword, new MockHttpServletResponse());
        storedVersion = OptionalLong.empty();
        MockHttpServletRequest withoutPassword = new MockHttpServletRequest();
        sessions.openRestricted(USER, withoutPassword, new MockHttpServletResponse());

        assertThat(storedAuthentication(withPassword).getPrincipal()).isEqualTo(new SealPrincipal(USER, VERSION));
        assertThat(storedAuthentication(withoutPassword).getPrincipal())
                .as("compte sans mot de passe (OAuth, lot 2) : rien à comparer")
                .isEqualTo(new SealPrincipal(USER));
    }

    @Test
    @DisplayName("AUTH-20 : une authentification sans compte SEAL n'ouvre aucune session (échec fermé)")
    void auth20AuthenticationWithoutSealAccountOpensNothing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatIllegalStateException()
                .isThrownBy(() -> sessions.open(
                        UsernamePasswordAuthenticationToken.authenticated("quelquun", null, List.of()),
                        request,
                        new MockHttpServletResponse()));
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("AUTH-53 : la fermeture invalide la session, vide le contexte et retire le jeton CSRF")
    void auth53CloseInvalidatesTheSessionAndDropsTheCsrfToken() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = requestWithSession(session);
        sessions.open(activeLogin(), request, new MockHttpServletResponse());
        MockHttpSession opened = (MockHttpSession) request.getSession(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        sessions.close(request, response);

        assertThat(opened.isInvalid()).isTrue();
        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getCookie("XSRF-TOKEN"))
                .isNotNull()
                .extracting(Cookie::getMaxAge)
                .isEqualTo(0);
    }

    @Test
    @DisplayName("AUTH-53 : fermer sans session ne fait rien d'autre que retirer le jeton CSRF")
    void auth53ClosingWithoutASessionIsHarmless() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        sessions.close(request, response);

        assertThat(request.getSession(false)).isNull();
        assertThat(response.getCookie("XSRF-TOKEN")).isNotNull();
    }

    @Test
    @DisplayName("AUTH-16 : seule la session restreinte du même compte peut devenir complète")
    void auth16OnlyTheSameRestrictedAccountIsUpgraded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        assertThatIllegalStateException()
                .isThrownBy(() -> sessions.upgradeToFull(USER, request, new MockHttpServletResponse()));

        sessions.openRestricted(USER, request, new MockHttpServletResponse());
        UserId other = new UserId(UUID.randomUUID());
        assertThatIllegalStateException()
                .isThrownBy(() -> sessions.upgradeToFull(other, request, new MockHttpServletResponse()));
    }

    @Test
    @DisplayName("AUTH-50 : le contexte de sécurité se sérialise, comme le fait Spring Session JDBC")
    void auth50SecurityContextIsSerializable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        sessions.openRestricted(USER, request, new MockHttpServletResponse());
        Object context = request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);

        SecurityContext copy = (SecurityContext) SerializationUtils.clone((java.io.Serializable) context);

        assertThat(copy.getAuthentication().getPrincipal()).isEqualTo(new SealPrincipal(USER, VERSION));
        assertThat(authorities(copy.getAuthentication())).containsExactly("ACCOUNT_PENDING_EMAIL", "FACTOR_PASSWORD");
    }
}
