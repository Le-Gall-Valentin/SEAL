package fr.vlegall.seal.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailHealthContributorAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * La santé du conteneur ne dépend pas du serveur SMTP : un envoi échoué ne touche jamais SEAL (ADR 0019, point 8), donc
 * sa sonde ne doit pas tomber avec Mailpit. Vérifié sur l'auto-configuration réelle de Spring Boot et sur
 * {@code application.yaml}.
 */
class MailHealthTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailHealthContributorAutoConfiguration.class))
            .withBean(JavaMailSenderImpl.class);

    @Test
    @DisplayName("AUTH-14 : la sonde de santé ne teste pas le serveur SMTP (A10)")
    void auth14HealthDoesNotProbeSmtp() {
        runner.run(context -> assertThat(context).doesNotHaveBean("mailHealthContributor"));
    }

    @Test
    @DisplayName("AUTH-14 : sans ce réglage, Spring Boot testerait le serveur SMTP à chaque sonde")
    void auth14WithoutTheSettingSpringBootWouldProbeSmtp() {
        runner.withPropertyValues("management.health.mail.enabled=true")
                .run(context -> assertThat(context).hasBean("mailHealthContributor"));
    }
}
