package fr.vlegall.seal.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.support.SealIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

@SealIntegrationTest
class NoDefaultUserIT {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("SEC-21 : aucun utilisateur par défaut de Spring Boot, donc aucun mot de passe généré dans le journal")
    void sec21NoDefaultUserWithAGeneratedPassword() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }
}
