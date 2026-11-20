package fr.vlegall.seal.support;

import fr.vlegall.seal.authentication.domain.model.OAuthProvider;
import fr.vlegall.seal.authentication.infrastructure.security.OAuthClientRegistrations;
import fr.vlegall.seal.common.OAuthClientProperties.Client;
import fr.vlegall.seal.common.SealProperties;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Google et GitHub configurés, vers le {@link FakeOAuthProvider} (ADR 0031, point 4). Les inscriptions sont celles de
 * production (portées, PKCE, redirection) ; seuls leurs points d'accès changent. Les tests qui l'importent partagent un
 * second contexte Spring.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OAuthTestConfiguration {

    public static final Client GOOGLE = new Client("google-client", "google-secret");
    public static final Client GITHUB = new Client("github-client", "github-secret");

    @Bean(destroyMethod = "close")
    FakeOAuthProvider fakeOAuthProvider() {
        return FakeOAuthProvider.start();
    }

    @Bean
    @Primary
    OAuthClientRegistrations fakeOAuthClientRegistrations(FakeOAuthProvider fake, SealProperties seal) {
        return new OAuthClientRegistrations(List.of(
                fake.google(OAuthClientRegistrations.registration(OAuthProvider.GOOGLE, GOOGLE, seal.origin()))
                        .build(),
                fake.github(OAuthClientRegistrations.registration(OAuthProvider.GITHUB, GITHUB, seal.origin()))
                        .build()));
    }
}
