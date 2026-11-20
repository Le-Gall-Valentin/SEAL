package fr.vlegall.seal.common;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AUTH-35, OPS-03, ADR 0032 (point 5) : applications OAuth de SEAL, lues dans les variables {@code SEAL_OAUTH_*}
 * ({@code seal.oauth}). Toutes sont facultatives : un fournisseur n'est configuré que si son identifiant et son secret
 * le sont, et une paire incomplète arrête le démarrage. Aucun secret n'apparaît dans un message ni dans un
 * {@code toString} (SEC-21, SEC-22). Distinct de {@link SealProperties} : seul le contexte {@code authentication} le
 * reçoit.
 */
@ConfigurationProperties("seal.oauth")
public final class OAuthClientProperties {

    private final @Nullable Client google;
    private final @Nullable Client github;

    /**
     * @param google {@code seal.oauth.google}, absent ou aux valeurs vides si l'application n'est pas configurée
     * @param github {@code seal.oauth.github}, de même
     */
    public OAuthClientProperties(@Nullable Pair google, @Nullable Pair github) {
        this.google = client("GOOGLE", google);
        this.github = client("GITHUB", github);
    }

    public Optional<Client> google() {
        return Optional.ofNullable(google);
    }

    public Optional<Client> github() {
        return Optional.ofNullable(github);
    }

    /** Une valeur vide, celle d'une variable d'environnement non renseignée, vaut une valeur absente. */
    private static @Nullable Client client(String provider, @Nullable Pair pair) {
        String id = pair == null || isBlank(pair.clientId()) ? null : pair.clientId();
        String secret = pair == null || isBlank(pair.clientSecret()) ? null : pair.clientSecret();
        if (id == null && secret == null) {
            return null;
        }
        String variables = "SEAL_OAUTH_" + provider + "_CLIENT_";
        if (id == null) {
            throw incomplete(variables + "ID", variables + "SECRET");
        }
        if (secret == null) {
            throw incomplete(variables + "SECRET", variables + "ID");
        }
        return new Client(id, secret);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /** Message fixe, qui nomme les variables sans citer leur valeur (SEC-21). */
    private static IllegalArgumentException incomplete(String missing, String given) {
        return new IllegalArgumentException(missing + " est vide alors que " + given
                + " est renseignée : renseignez les deux, ou aucune (OPS-03, ADR 0032)");
    }

    @Override
    public String toString() {
        return "OAuthClientProperties[google=" + (google == null ? "absent" : "configuré") + ", github="
                + (github == null ? "absent" : "configuré") + "]";
    }

    /** Valeurs telles que liées depuis la configuration, éventuellement vides. */
    public record Pair(@Nullable String clientId, @Nullable String clientSecret) {

        @Override
        public String toString() {
            return "Pair[clientId=" + clientId + ", clientSecret=***]";
        }
    }

    /** Identifiant et secret d'une application OAuth configurée. */
    public record Client(String clientId, String clientSecret) {

        public Client {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(clientSecret, "clientSecret");
        }

        @Override
        public String toString() {
            return "Client[clientId=" + clientId + ", clientSecret=***]";
        }
    }
}
