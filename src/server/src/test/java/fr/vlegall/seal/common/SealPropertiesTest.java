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

    @Test
    @DisplayName("TRN-02 : l'origine WebSocket dérive de l'origine HTTPS unique")
    void trn02WebSocketOriginDerivesFromTheHttpsOrigin() {
        SealProperties properties = new SealProperties(URI.create("https://localhost:8443"), SECURITY);

        assertThat(properties.webSocketOrigin()).isEqualTo("wss://localhost:8443");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"http://localhost:8443", "https://localhost:8443/app", "https://localhost:8443?x=1", "localhost"
            })
    @DisplayName("TRN-02 : seule une origine https sans chemin est acceptée")
    void trn02RejectsAnythingButABareHttpsOrigin(String origin) {
        URI uri = URI.create(origin);

        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties(uri, SECURITY));
    }

    @Test
    @DisplayName("TRN-03 : une durée HSTS nulle ou négative est refusée")
    void trn03RejectsANonPositiveHstsDuration() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Security(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Security(Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("Les paramètres obligatoires manquants empêchent le démarrage")
    void missingSettingsPreventStartup() {
        URI origin = URI.create("https://localhost:8443");

        assertThatNullPointerException().isThrownBy(() -> new SealProperties(null, SECURITY));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties(origin, null));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Security(null));
    }
}
