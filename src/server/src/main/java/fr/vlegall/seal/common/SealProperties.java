package fr.vlegall.seal.common;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres propres à SEAL, sous le préfixe {@code seal}.
 *
 * @param origin origine unique du client, de l'API et de la WebSocket (TRN-02), par exemple {@code https://localhost:8443}
 * @param security paramètres des en-têtes de sécurité
 */
@ConfigurationProperties("seal")
public record SealProperties(URI origin, Security security) {

    public SealProperties {
        Objects.requireNonNull(origin, "seal.origin est obligatoire");
        Objects.requireNonNull(security, "seal.security est obligatoire");
        // TRN-02 : une origine HTTPS, sans chemin, sert à la fois l'API, le client et la WebSocket.
        if (!"https".equals(origin.getScheme())
                || origin.getHost() == null
                || !(origin.getRawPath() == null || origin.getRawPath().isEmpty())
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null) {
            throw new IllegalArgumentException("seal.origin doit être une origine https sans chemin : " + origin);
        }
    }

    /** Origine de la WebSocket sécurisée, citée dans la directive {@code connect-src} de la CSP (section 12.3). */
    public String webSocketOrigin() {
        return "wss://" + origin.getRawAuthority();
    }

    /** @param hstsMaxAge durée de l'en-tête HSTS (TRN-03) : 300 secondes en profil demo, 1 an en profil prod */
    public record Security(Duration hstsMaxAge) {

        public Security {
            Objects.requireNonNull(hstsMaxAge, "seal.security.hsts-max-age est obligatoire");
            if (hstsMaxAge.isNegative() || hstsMaxAge.isZero()) {
                throw new IllegalArgumentException("seal.security.hsts-max-age doit être positif : " + hstsMaxAge);
            }
        }
    }
}
