package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SealPropertiesTest {

    private static final SealProperties.Security SECURITY = new SealProperties.Security(Duration.ofSeconds(300));
    private static final SealProperties.Session SESSION = new SealProperties.Session(Duration.ofHours(12));
    private static final SealProperties.Mail MAIL = new SealProperties.Mail("SEAL <noreply@seal.localhost>");

    @Test
    @DisplayName("TRN-02 : l'origine WebSocket dérive de l'origine HTTPS unique")
    void trn02WebSocketOriginDerivesFromTheHttpsOrigin() {
        SealProperties properties = new SealProperties(URI.create("https://localhost:8443"), SECURITY, SESSION, MAIL);

        assertThat(properties.webSocketOrigin()).isEqualTo("wss://localhost:8443");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost:8443",
                "https://localhost:8443/app",
                "https://localhost:8443/",
                "https://localhost:8443?x=1",
                "https://localhost:8443#fragment",
                "https://u:p@localhost:8443",
                "https://LOCALHOST:8443",
                "https://localhost:443",
                "https:opaque",
                "localhost"
            })
    @DisplayName("TRN-02 : seule une origine https canonique, sans chemin ni identifiants, est acceptée")
    void trn02RejectsAnythingButABareHttpsOrigin(String origin) {
        URI uri = URI.create(origin);

        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties(uri, SECURITY, SESSION, MAIL));
    }

    @Test
    @DisplayName("TRN-03 : une durée HSTS nulle ou négative est refusée")
    void trn03RejectsANonPositiveHstsDuration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Security(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Security(Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("TRN-02, TRN-03, AUTH-52 : les paramètres obligatoires manquants empêchent le démarrage")
    void missingSettingsPreventStartup() {
        URI origin = URI.create("https://localhost:8443");

        assertThatNullPointerException().isThrownBy(() -> new SealProperties(null, SECURITY, SESSION, MAIL));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties(origin, null, SESSION, MAIL));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Security(null));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties(origin, SECURITY, null, MAIL));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Session(null));
    }

    @Test
    @DisplayName("AUTH-52 : la durée de vie absolue d'une session doit être positive")
    void auth52RejectsANonPositiveAbsoluteTimeout() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Session(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Session(Duration.ofHours(-1)));
    }

    @Test
    @DisplayName("AUTH-14 : l'expéditeur des courriels est obligatoire et non vide")
    void auth14MailSenderIsRequired() {
        URI origin = URI.create("https://localhost:8443");
        assertThatNullPointerException().isThrownBy(() -> new SealProperties(origin, SECURITY, SESSION, null));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Mail(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Mail(" "));
    }
}
