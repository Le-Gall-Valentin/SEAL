package fr.vlegall.seal.authentication.infrastructure.security;

import fr.vlegall.seal.authentication.application.port.in.LoginUseCase;
import fr.vlegall.seal.authentication.domain.model.LoginOutcome;
import fr.vlegall.seal.security.session.SealAuthorities;
import fr.vlegall.seal.security.session.SealPrincipal;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Seul fournisseur de l'{@code AuthenticationManager} (AUTH-20, ADR 0021 point 2) : il délègue la vérification au cas
 * d'usage {@link LoginUseCase}. L'authentification rendue porte le compte et son statut ; la session est ouverte
 * ensuite par la couche web.
 */
@Component
public class LoginAuthenticationProvider implements AuthenticationProvider {

    private final LoginUseCase login;

    public LoginAuthenticationProvider(LoginUseCase login) {
        this.login = login;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String password = authentication.getCredentials() instanceof String text ? text : "";
        return switch (login.login(authentication.getName(), password)) {
            case LoginOutcome.Authenticated(var user, var verified) ->
                UsernamePasswordAuthenticationToken.authenticated(
                        new SealPrincipal(user),
                        null,
                        List.of(new SimpleGrantedAuthority(
                                verified ? SealAuthorities.ACCOUNT_ACTIVE : SealAuthorities.ACCOUNT_PENDING_EMAIL)));
            case LoginOutcome.Rejected rejected -> throw new BadCredentialsException("connexion refusée");
        };
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
