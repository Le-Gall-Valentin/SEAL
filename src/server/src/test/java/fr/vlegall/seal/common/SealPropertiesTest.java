package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SealPropertiesTest {

    private static final SealProperties.Security SECURITY = new SealProperties.Security(Duration.ofSeconds(300));
    private static final SealProperties.Session SESSION =
            new SealProperties.Session(Duration.ofHours(12), Duration.ofMinutes(10));
    private static final SealProperties.Mail MAIL = new SealProperties.Mail("SEAL <noreply@seal.localhost>");
    private static final SealProperties.Ws WS = new SealProperties.Ws(Duration.ofSeconds(10));
    private static final Path DATA = Path.of("data");

    @Test
    @DisplayName("TRN-02 : l'origine WebSocket dérive de l'origine HTTPS unique")
    void trn02WebSocketOriginDerivesFromTheHttpsOrigin() {
        SealProperties properties =
                new SealProperties(URI.create("https://localhost:8443"), SECURITY, SESSION, MAIL, WS, DATA);

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

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SealProperties(uri, SECURITY, SESSION, MAIL, WS, DATA));
    }

    @Test
    @DisplayName("TRN-12, ADR 0044 : la ronde des WebSockets se règle entre une seconde et une heure")
    void trn12SweepDelayIsBounded() {
        assertThat(WS.sweepDelay()).isEqualTo(Duration.ofSeconds(10));
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Ws(Duration.ofMillis(999)));
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Ws(Duration.ofSeconds(-1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SealProperties.Ws(Duration.ofHours(1).plusSeconds(1)));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Ws(null));
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

        assertThatNullPointerException().isThrownBy(() -> new SealProperties(null, SECURITY, SESSION, MAIL, WS, DATA));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties(origin, null, SESSION, MAIL, WS, DATA));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Security(null));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties(origin, SECURITY, null, MAIL, WS, DATA));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Session(null, Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("AUTH-52 : la durée de vie absolue d'une session doit être positive")
    void auth52RejectsANonPositiveAbsoluteTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SealProperties.Session(Duration.ZERO, Duration.ofMinutes(10)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SealProperties.Session(Duration.ofHours(-1), Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("AUTH-55 : la durée du mode sudo est obligatoire et positive")
    void auth55RejectsAMissingOrNonPositiveSudoWindow() {
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Session(Duration.ofHours(12), null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SealProperties.Session(Duration.ofHours(12), Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SealProperties.Session(Duration.ofHours(12), Duration.ofMinutes(-1)));
    }

    @Test
    @DisplayName("AUTH-14 : l'expéditeur des courriels est obligatoire et non vide")
    void auth14MailSenderIsRequired() {
        URI origin = URI.create("https://localhost:8443");
        assertThatNullPointerException()
                .isThrownBy(() -> new SealProperties(origin, SECURITY, SESSION, null, WS, DATA));
        assertThatNullPointerException()
                .isThrownBy(() -> new SealProperties(origin, SECURITY, SESSION, MAIL, null, DATA));
        assertThatNullPointerException().isThrownBy(() -> new SealProperties.Mail(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new SealProperties.Mail(" "));
    }

    @Test
    @DisplayName("KEY-10 : le dossier des données que SEAL génère lui-même est obligatoire")
    void key10DataDirectoryIsRequired() {
        URI origin = URI.create("https://localhost:8443");

        assertThat(new SealProperties(origin, SECURITY, SESSION, MAIL, WS, DATA).dataDir())
                .isEqualTo(DATA);
        assertThatNullPointerException()
                .isThrownBy(() -> new SealProperties(origin, SECURITY, SESSION, MAIL, WS, (Path) null));
        // Constructeur de liaison (KEY-10) : un seal.data-dir absent est refusé avec le même message.
        assertThatNullPointerException()
                .isThrownBy(() -> new SealProperties(origin, SECURITY, SESSION, MAIL, WS, (String) null))
                .withMessageContaining("seal.data-dir");
    }
}
