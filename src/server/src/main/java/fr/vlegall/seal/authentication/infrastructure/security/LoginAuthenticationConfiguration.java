package fr.vlegall.seal.authentication.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;

/** AUTH-20 : l'{@code AuthenticationManager} de SEAL n'a qu'un fournisseur, {@link LoginAuthenticationProvider}. */
@Configuration(proxyBeanMethods = false)
public class LoginAuthenticationConfiguration {

    @Bean
    AuthenticationManager authenticationManager(LoginAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }
}
