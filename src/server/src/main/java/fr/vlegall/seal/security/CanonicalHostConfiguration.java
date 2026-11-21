package fr.vlegall.seal.security;

import fr.vlegall.seal.common.SealProperties;
import java.net.URI;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TRN-02, A02 : le serveur ne connaît qu'un hôte, celui de {@code seal.origin}. Tomcat rend une redirection absolue
 * quand il ne peut pas la laisser relative (HTTP/1.0) ; sans ce réglage, il la construirait sur l'en-tête {@code Host},
 * que choisit le client. Le connecteur rend donc toujours l'hôte et le port de l'origine.
 */
@Configuration(proxyBeanMethods = false)
class CanonicalHostConfiguration {

    @Bean
    TomcatConnectorCustomizer canonicalHost(SealProperties properties) {
        URI origin = properties.origin();
        int port = origin.getPort() == -1 ? 443 : origin.getPort();
        return connector -> {
            connector.setProxyName(origin.getHost());
            connector.setProxyPort(port);
        };
    }
}
