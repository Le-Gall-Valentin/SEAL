package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import fr.vlegall.seal.common.UserId;
import fr.vlegall.seal.support.MutableClock;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
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

    private final MutableClock clock = MutableClock.fixedAt(NOW);
    private final SessionEstablisher sessions =
            new SessionEstablisher(CookieCsrfTokenRepository.withHttpOnlyFalse(), clock);

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
        assertThat(authentication.getPrincipal()).isEqualTo(new SealPrincipal(USER));
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
    @DisplayName("AUTH-20 : la connexion d'un compte actif ouvre une session complète, sous un nouvel identifiant")
    void auth20FullSessionCarriesActiveStatusAndPasswordFactor() {
        MockHttpSession anonymous = new MockHttpSession(null, "session-anonyme");
        MockHttpServletRequest request = requestWithSession(anonymous);
        MockHttpServletResponse response = new MockHttpServletResponse();

        sessions.openFull(USER, request, response);

        Authentication authentication = storedAuthentication(request);
        assertThat(request.getSession(false).getId()).isNotEqualTo("session-anonyme");
        assertThat(authentication.getPrincipal()).isEqualTo(new SealPrincipal(USER));
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
        sessions.openFull(USER, request, new MockHttpServletResponse());
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

        assertThat(copy.getAuthentication().getPrincipal()).isEqualTo(new SealPrincipal(USER));
        assertThat(authorities(copy.getAuthentication())).containsExactly("ACCOUNT_PENDING_EMAIL", "FACTOR_PASSWORD");
    }
}
