package fr.vlegall.seal.security;

import static org.assertj.core.api.Assertions.assertThat;

import fr.vlegall.seal.common.SealProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** TRN-02, A02 : le connecteur rend toujours l'hôte et le port de l'origine unique. */
@DisplayName("TRN-02 : hôte canonique du connecteur")
class CanonicalHostConfigurationTest {

    private static Connector customized(String origin) {
        SealProperties properties = new SealProperties(
                URI.create(origin),
                new SealProperties.Security(Duration.ofMinutes(5)),
                new SealProperties.Session(Duration.ofHours(12), Duration.ofMinutes(10)),
                new SealProperties.Mail("SEAL <noreply@seal.localhost>"),
                new SealProperties.Ws(Duration.ofSeconds(10)),
                Path.of("data"));
        Connector connector = new Connector();
        new CanonicalHostConfiguration().canonicalHost(properties).customize(connector);
        return connector;
    }

    @Test
    @DisplayName("TRN-02 : l'hôte et le port de seal.origin")
    void trn02HostAndPortOfTheOrigin() {
        Connector connector = customized("https://localhost:8443");

        assertThat(connector.getProxyName()).isEqualTo("localhost");
        assertThat(connector.getProxyPort()).isEqualTo(8443);
    }

    @Test
    @DisplayName("TRN-02 : sans port dans seal.origin, le port de HTTPS, 443")
    void trn02DefaultHttpsPort() {
        Connector connector = customized("https://seal.exemple");

        assertThat(connector.getProxyName()).isEqualTo("seal.exemple");
        assertThat(connector.getProxyPort()).isEqualTo(443);
    }
}
