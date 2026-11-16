package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** AUTH-13, SEC-21, SEC-22, ADR 0026 : poivre des mots de passe, lu dans SEAL_PASSWORD_PEPPER. */
@DisplayName("AUTH-13 : poivre des mots de passe")
class PasswordPepperTest {

    private static final byte[] BYTES = new byte[32];

    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) (i + 1);
        }
    }

    private static final String ENCODED = Base64.getEncoder().encodeToString(BYTES);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordPepper.class)
    static class Binding {}

    private final ApplicationContextRunner context =
            new ApplicationContextRunner().withUserConfiguration(Binding.class);

    @Test
    @DisplayName("AUTH-13 : 32 octets en base64 donnent le poivre, rendu en copie")
    void auth13ThirtyTwoBytesMakeThePepper() {
        PasswordPepper pepper = new PasswordPepper(ENCODED);

        assertThat(pepper.value()).isEqualTo(BYTES);
        pepper.value()[0] = 99;
        assertThat(pepper.value()).isEqualTo(BYTES);
    }

    @Test
    @DisplayName("AUTH-13, ADR 0026 : un poivre absent, illisible ou d'une autre taille est refusé, sans être répété")
    void auth13MissingOrMalformedPepperIsRefused() {
        String notBase64 = "pas-du-base64-secret!";
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new PasswordPepper(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEAL_PASSWORD_PEPPER est vide");
        assertThatThrownBy(() -> new PasswordPepper(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEAL_PASSWORD_PEPPER est vide");
        for (int length : new int[] {31, 33, 64}) {
            String wrongSize = Base64.getEncoder().encodeToString(new byte[length]);
            assertThatThrownBy(() -> new PasswordPepper(wrongSize))
                    .as("%d octets", length)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 octets")
                    .hasMessageNotContaining(wrongSize);
        }
        assertThatThrownBy(() -> new PasswordPepper(notBase64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(notBase64)
                .hasNoCause();
        assertThatThrownBy(() -> new PasswordPepper(sixteenBytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(sixteenBytes);
    }

    @Test
    @DisplayName("SEC-21 : le poivre n'apparaît jamais dans toString")
    void sec21PepperNeverShows() {
        PasswordPepper pepper = new PasswordPepper(ENCODED);

        assertThat(pepper.toString()).doesNotContain(ENCODED).contains("***");
    }

    @Test
    @DisplayName("SEC-22, ADR 0026 : sans seal.passwords.pepper, le démarrage échoue ; avec, le poivre est lié")
    void sec22StartupNeedsThePepper() {
        context.run(started -> assertThat(started).hasFailed());
        context.withPropertyValues("seal.passwords.pepper=" + ENCODED)
                .run(started -> assertThat(started.getBean(PasswordPepper.class).value())
                        .hasSize(32));
    }
}
