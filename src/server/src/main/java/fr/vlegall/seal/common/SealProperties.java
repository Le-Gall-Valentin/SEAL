package fr.vlegall.seal.common;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Paramètres propres à SEAL, sous le préfixe {@code seal}.
 *
 * @param origin origine unique du client, de l'API et de la WebSocket (TRN-02), par exemple {@code https://localhost:8443}
 * @param security paramètres des en-têtes de sécurité
 * @param session durées de vie des sessions
 * @param mail expédition des courriels
 * @param dataDir dossier de ce que SEAL génère lui-même, comme la clé de l'Autorité (KEY-10) : {@code /data} dans
 *     l'image (volume {@code app-data}), {@code ../data}, soit {@code src/data}, hors de Git, en local
 */
@ConfigurationProperties("seal")
public record SealProperties(URI origin, Security security, Session session, Mail mail, Path dataDir) {

    public SealProperties {
        Objects.requireNonNull(origin, "seal.origin est obligatoire");
        Objects.requireNonNull(security, "seal.security est obligatoire");
        Objects.requireNonNull(session, "seal.session est obligatoire");
        Objects.requireNonNull(mail, "seal.mail est obligatoire");
        Objects.requireNonNull(dataDir, "seal.data-dir est obligatoire");
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

    /**
     * Constructeur de liaison : {@code seal.data-dir} arrive en texte et devient un chemin de fichiers par
     * {@link Path#of}. La conversion par défaut de Spring passerait par le chargeur de ressources du contexte, qui est,
     * dans le serveur web, le contexte servlet : {@code ../data} y était refusé, et une ressource acceptée aurait désigné
     * un autre dossier (KEY-10, ADR 0025 point 12).
     */
    @ConstructorBinding
    public SealProperties(URI origin, Security security, Session session, Mail mail, String dataDir) {
        this(
                origin,
                security,
                session,
                mail,
                Path.of(Objects.requireNonNull(dataDir, "seal.data-dir est obligatoire")));
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

    /**
     * @param absoluteTimeout durée de vie maximale d'une session depuis sa création : 12 heures (AUTH-52)
     * @param sudoWindow âge maximal du facteur qui ouvre le mode sudo : 10 minutes (AUTH-55, ADR 0034)
     */
    public record Session(Duration absoluteTimeout, Duration sudoWindow) {

        public Session {
            Objects.requireNonNull(absoluteTimeout, "seal.session.absolute-timeout est obligatoire");
            Objects.requireNonNull(sudoWindow, "seal.session.sudo-window est obligatoire");
            if (absoluteTimeout.isNegative() || absoluteTimeout.isZero()) {
                throw new IllegalArgumentException(
                        "seal.session.absolute-timeout doit être positive : " + absoluteTimeout);
            }
            if (sudoWindow.isNegative() || sudoWindow.isZero()) {
                throw new IllegalArgumentException("seal.session.sudo-window doit être positive : " + sudoWindow);
            }
        }
    }

    /** @param from expéditeur des courriels de SEAL (ADR 0019, point 8) */
    public record Mail(String from) {

        public Mail {
            Objects.requireNonNull(from, "seal.mail.from est obligatoire");
            if (from.isBlank()) {
                throw new IllegalArgumentException("seal.mail.from ne doit pas être vide");
            }
        }
    }
}
