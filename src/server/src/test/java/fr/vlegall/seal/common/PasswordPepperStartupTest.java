package fr.vlegall.seal.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * SEC-21, SEC-22, ADR 0026 (points 5 et 8) : un vrai démarrage, qui lit {@code application.yaml}. Sans la variable,
 * il échoue en la nommant ; avec une valeur invalide, il échoue sans jamais l'écrire dans le rapport d'échec.
 */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("SEC-22 : démarrage et poivre des mots de passe")
class PasswordPepperStartupTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordPepper.class)
    static class Binding {}

    private static SpringApplication application(StandardEnvironment environment) {
        SpringApplication application = new SpringApplication(Binding.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setEnvironment(environment);
        return application;
    }

    /** Environnement du processus remplacé par {@code variables} : rien ne dépend de la machine qui lance le test. */
    private static StandardEnvironment environment(Map<String, Object> variables) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("variables", variables));
        return environment;
    }

    @Test
    @DisplayName("SEC-22, ADR 0026 (point 8) : sans SEAL_PASSWORD_PEPPER, le démarrage échoue en nommant la variable")
    void sec22MissingVariableIsNamed(CapturedOutput output) {
        assertThatThrownBy(() -> application(environment(Map.of())).run()).isNotNull();

        assertThat(output.getAll()).contains("SEAL_PASSWORD_PEPPER est vide");
    }

    static Stream<Arguments> invalidPeppers() {
        String valid = Base64.getEncoder().encodeToString(new byte[32]).replace('A', 'Q');
        return Stream.of(
                Arguments.of(
                        "31 octets",
                        Base64.getEncoder().encodeToString(new byte[31]).replace('A', 'R')),
                Arguments.of(
                        "33 octets",
                        Base64.getEncoder().encodeToString(new byte[33]).replace('A', 'S')),
                Arguments.of("retour chariot final", valid + "\r"),
                Arguments.of("base64url", valid.replace('Q', '-') + "_"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPeppers")
    @DisplayName("SEC-21, ADR 0026 (point 5) : un poivre invalide arrête le démarrage sans être écrit")
    void sec21InvalidPepperNeverShows(String label, String pepper, CapturedOutput output) {
        assertThatThrownBy(() -> application(environment(Map.of("SEAL_PASSWORD_PEPPER", pepper)))
                        .run())
                .isNotNull();

        assertThat(output.getAll()).as(label).doesNotContain(pepper.strip());
    }
}
