package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.vlegall.seal.common.OAuthClientProperties.Pair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** OPS-03, AUTH-35, SEC-21, ADR 0032 (point 5) : identifiants des applications OAuth, tous facultatifs. */
@DisplayName("OPS-03 : identifiants des applications OAuth")
class OAuthClientPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OAuthClientProperties.class)
    static class Binding {}

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(Binding.class);

    @Test
    @DisplayName("OPS-03 : sans aucune valeur, aucun fournisseur n'est configuré")
    void ops03NothingConfigured() {
        OAuthClientProperties properties = new OAuthClientProperties(null, null);

        assertThat(properties.google()).isEmpty();
        assertThat(properties.github()).isEmpty();
    }

    @Test
    @DisplayName("OPS-03 : des variables vides valent des variables absentes")
    void ops03BlankValuesMeanAbsent() {
        OAuthClientProperties properties = new OAuthClientProperties(new Pair("", " "), new Pair(null, ""));

        assertThat(properties.google()).isEmpty();
        assertThat(properties.github()).isEmpty();
    }

    @Test
    @DisplayName("AUTH-35 : un identifiant et un secret configurent le fournisseur")
    void auth35PairConfiguresTheProvider() {
        OAuthClientProperties properties =
                new OAuthClientProperties(new Pair("google-id", "google-secret"), new Pair("github-id", "gh-secret"));

        assertThat(properties.google()).hasValueSatisfying(client -> {
            assertThat(client.clientId()).isEqualTo("google-id");
            assertThat(client.clientSecret()).isEqualTo("google-secret");
        });
        assertThat(properties.github()).hasValueSatisfying(client -> {
            assertThat(client.clientId()).isEqualTo("github-id");
            assertThat(client.clientSecret()).isEqualTo("gh-secret");
        });
    }

    @Test
    @DisplayName("ADR 0032 : un identifiant sans secret arrête le démarrage, en nommant la variable manquante")
    void adr0032IdWithoutSecretIsRefused() {
        assertThatThrownBy(() -> new OAuthClientProperties(new Pair("google-id", ""), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEAL_OAUTH_GOOGLE_CLIENT_SECRET");
    }

    @Test
    @DisplayName("ADR 0032 : un secret sans identifiant arrête le démarrage, sans citer le secret (SEC-21)")
    void adr0032SecretWithoutIdIsRefused() {
        assertThatThrownBy(() -> new OAuthClientProperties(null, new Pair(null, "gh-secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEAL_OAUTH_GITHUB_CLIENT_ID")
                .message()
                .doesNotContain("gh-secret");
    }

    @Test
    @DisplayName("SEC-21 : aucun secret dans toString")
    void sec21SecretsNeverPrinted() {
        OAuthClientProperties properties =
                new OAuthClientProperties(new Pair("google-id", "google-secret"), new Pair("github-id", "gh-secret"));

        assertThat(properties.toString()).doesNotContain("google-secret", "gh-secret");
        assertThat(properties.google().orElseThrow().toString()).doesNotContain("google-secret");
        assertThat(new Pair("id", "pair-secret").toString()).doesNotContain("pair-secret");
    }

    @Test
    @DisplayName("ADR 0032 : liaison de seal.oauth, et refus d'une paire incomplète au démarrage")
    void adr0032BindsFromSealOauth() {
        context.withPropertyValues(
                        "seal.oauth.google.client-id=google-id",
                        "seal.oauth.google.client-secret=google-secret",
                        "seal.oauth.github.client-id=",
                        "seal.oauth.github.client-secret=")
                .run(started -> {
                    OAuthClientProperties properties = started.getBean(OAuthClientProperties.class);
                    assertThat(properties.google()).isPresent();
                    assertThat(properties.github()).isEmpty();
                });
        context.withPropertyValues("seal.oauth.github.client-id=github-id")
                .run(started -> assertThat(started)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("SEAL_OAUTH_GITHUB_CLIENT_SECRET"));
    }
}
