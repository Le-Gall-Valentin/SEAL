package fr.vlegall.seal.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CredentialVersionFilterTest {

    private static final UserId USER = new UserId(UUID.randomUUID());

    private final List<UserId> lookups = new ArrayList<>();
    private OptionalLong stored = OptionalLong.of(3);
    private final CredentialVersionFilter filter = new CredentialVersionFilter(user -> {
        lookups.add(user);
        return stored;
    });
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpSession session = new MockHttpSession();

    CredentialVersionFilterTest() {
        request.setSession(session);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signIn(SealPrincipal principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private MockFilterChain run() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    @DisplayName("AUTH-70 : une session ouverte avec la version courante du mot de passe continue (ADR 0023)")
    void auth70CurrentVersionKeepsTheSession() throws Exception {
        signIn(new SealPrincipal(USER, 3L));

        MockFilterChain chain = run();

        assertThat(session.isInvalid()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(lookups).containsExactly(USER);
    }

    @Test
    @DisplayName("AUTH-70 : une session ouverte avec une ancienne version du mot de passe tombe (ADR 0023)")
    void auth70StaleVersionDropsTheSession() throws Exception {
        signIn(new SealPrincipal(USER, 2L));

        MockFilterChain chain = run();

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("la requête continue, sans session").isNotNull();
    }

    @Test
    @DisplayName("AUTH-70 : une session d'un compte qui n'a plus de mot de passe tombe (échec fermé)")
    void auth70MissingCredentialDropsTheSession() throws Exception {
        stored = OptionalLong.empty();
        signIn(new SealPrincipal(USER, 1L));

        run();

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("AUTH-16 : une session sans version (inscription, vérification) n'est pas comparée (ADR 0023)")
    void auth16UnversionedSessionIsNotChecked() throws Exception {
        signIn(new SealPrincipal(USER));

        run();

        assertThat(session.isInvalid()).isFalse();
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("AUTH-60 : une requête anonyme passe sans lecture de la base")
    void auth60AnonymousRequestIsNotChecked() throws Exception {
        MockFilterChain chain = run();

        assertThat(chain.getRequest()).isNotNull();
        assertThat(lookups).isEmpty();
    }
}
