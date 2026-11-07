package fr.vlegall.seal.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * TRN-01 (ADR 0014, point 3) : TLS n'est pas une option. Sans {@code server.ssl.bundle}, le serveur refuse de démarrer,
 * sauf en profil {@code test} : une faute de profil n'ouvre jamais un serveur en HTTP clair (échec fermé). La
 * vérification a lieu à la création des composants, avant que le serveur web n'écoute.
 */
@Configuration(proxyBeanMethods = false)
final class TlsRequiredConfiguration {

    TlsRequiredConfiguration(Environment environment) {
        if (!environment.matchesProfiles("test")
                && !StringUtils.hasText(environment.getProperty("server.ssl.bundle"))) {
            throw new IllegalStateException("TLS obligatoire (TRN-01) : server.ssl.bundle absent hors profil test ; "
                    + "activer le profil demo ou prod");
        }
    }
}
