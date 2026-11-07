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
 * @param session durées de vie des sessions
 */
@ConfigurationProperties("seal")
public record SealProperties(URI origin, Security security, Session session) {

    public SealProperties {
        Objects.requireNonNull(origin, "seal.origin est obligatoire");
        Objects.requireNonNull(security, "seal.security est obligatoire");
        Objects.requireNonNull(session, "seal.session est obligatoire");
        // TRN-02 : une origine HTTPS canonique, sans chemin ni identifiants, sert à la fois l'API, le client et la
        // WebSocket ; elle doit être écrite comme le navigateur l'envoie dans l'en-tête Origin (hôte en minuscules,
        // port
        // 443 implicite), sinon la comparaison de TRN-11 échouerait.
        if (!"https".equals(origin.getScheme())
                || origin.getHost() == null
                || origin.getHost().chars().anyMatch(c -> c >= 'A' && c <= 'Z')
                || origin.getRawUserInfo() != null
                || origin.getPort() == 443
                || !(origin.getRawPath() == null || origin.getRawPath().isEmpty())
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "seal.origin doit être une origine https canonique sans chemin : " + origin);
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

    /** @param absoluteTimeout durée de vie maximale d'une session depuis sa création : 12 heures (AUTH-52) */
    public record Session(Duration absoluteTimeout) {

        public Session {
            Objects.requireNonNull(absoluteTimeout, "seal.session.absolute-timeout est obligatoire");
            if (absoluteTimeout.isNegative() || absoluteTimeout.isZero()) {
                throw new IllegalArgumentException(
                        "seal.session.absolute-timeout doit être positive : " + absoluteTimeout);
            }
        }
    }
}
