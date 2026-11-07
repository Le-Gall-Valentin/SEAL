package fr.vlegall.seal.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TlsRequiredConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TlsRequiredConfiguration.class);

    @Test
    @DisplayName("TRN-01 : sans bundle TLS et hors profil test, le serveur refuse de démarrer")
    void trn01StartupFailsWithoutTls() {
        runner.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TLS"));
    }

    @Test
    @DisplayName("TRN-01 : un profil inattendu sans bundle TLS ne démarre pas non plus")
    void trn01UnexpectedProfileDoesNotStartWithoutTls() {
        runner.withPropertyValues("spring.profiles.active=production")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("TRN-01 : avec le bundle TLS, le serveur démarre")
    void trn01StartupSucceedsWithTls() {
        runner.withPropertyValues("server.ssl.bundle=seal-tls")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("TRN-01 : le profil test, sans TLS, démarre pour les tests d'intégration")
    void trn01TestProfileStartsWithoutTls() {
        runner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
